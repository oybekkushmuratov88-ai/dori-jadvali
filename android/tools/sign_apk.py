#!/usr/bin/env python3
"""Sign an APK with both the v1 (JAR) and v2 (APK Signature Scheme v2) schemes.

usage: KEYSTORE_PASS=... sign_apk.py <keystore.p12> <in.apk> <out.apk>

v1 is kept for installers and security scanners that still read META-INF; Android 7.0+
verifies the v2 block, which covers the whole file (including the v1 files).
"""
import base64
import hashlib
import os
import struct
import sys
import zipfile

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import pkcs7, pkcs12

SIG_NAME = "DORI"
V2_BLOCK_ID = 0x7109871A
RSA_PKCS1_SHA256 = 0x0103
CHUNK = 1024 * 1024


def manifest_line(text):
    """JAR manifest lines are at most 72 bytes; longer ones continue after a leading space."""
    raw = text.encode()
    out = [raw[:70]]
    raw = raw[70:]
    while raw:
        out.append(b" " + raw[:69])
        raw = raw[69:]
    return b"".join(part + b"\r\n" for part in out)


def b64sha256(data):
    return base64.b64encode(hashlib.sha256(data).digest()).decode()


def add_v1(zin, zout, key, cert):
    main = manifest_line("Manifest-Version: 1.0") + manifest_line("Created-By: 1.0 (Dori jadvali)") + b"\r\n"
    sections = []
    for info in zin.infolist():
        if info.filename.startswith("META-INF/") or info.filename.endswith("/"):
            continue
        section = manifest_line("Name: " + info.filename) + manifest_line("SHA-256-Digest: " + b64sha256(zin.read(info.filename))) + b"\r\n"
        sections.append((info.filename, section))
    manifest = main + b"".join(s for _, s in sections)

    sf = (manifest_line("Signature-Version: 1.0")
          + manifest_line("Created-By: 1.0 (Dori jadvali)")
          + manifest_line("SHA-256-Digest-Manifest: " + b64sha256(manifest))
          + manifest_line("SHA-256-Digest-Manifest-Main-Attributes: " + b64sha256(main))
          + manifest_line("X-Android-APK-Signed: 2")
          + b"\r\n")
    for name, section in sections:
        sf += manifest_line("Name: " + name) + manifest_line("SHA-256-Digest: " + b64sha256(section)) + b"\r\n"

    block = (pkcs7.PKCS7SignatureBuilder().set_data(sf).add_signer(cert, key, hashes.SHA256())
             .sign(serialization.Encoding.DER, [pkcs7.PKCS7Options.DetachedSignature, pkcs7.PKCS7Options.NoCapabilities]))

    for name, data in (("META-INF/MANIFEST.MF", manifest), ("META-INF/%s.SF" % SIG_NAME, sf), ("META-INF/%s.RSA" % SIG_NAME, block)):
        info = zipfile.ZipInfo(name, (2008, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(info, data)


def copy_aligned(zin, raw, zout):
    """Copy entries unchanged; stored entries start on a 4-byte boundary (zipalign's 0xD935 extra field)."""
    for info in zin.infolist():
        data = zin.read(info.filename)
        out = zipfile.ZipInfo(info.filename, (2008, 1, 1, 0, 0, 0))
        out.compress_type = info.compress_type
        if info.compress_type == zipfile.ZIP_STORED:
            base = raw.tell() + 30 + len(info.filename.encode())
            pad = (-(base + 6)) % 4
            out.extra = struct.pack("<HHH", 0xD935, 2 + pad, 4) + b"\0" * pad
        zout.writestr(out, data)


def lp(data):
    return struct.pack("<I", len(data)) + data


def chunked_digest(sections):
    digests = []
    for section in sections:
        for i in range(0, len(section), CHUNK):
            chunk = section[i:i + CHUNK]
            digests.append(hashlib.sha256(b"\xa5" + struct.pack("<I", len(chunk)) + chunk).digest())
    return hashlib.sha256(b"\x5a" + struct.pack("<I", len(digests)) + b"".join(digests)).digest()


def add_v2(apk, key, cert):
    eocd = len(apk) - 22
    if apk[eocd:eocd + 4] != b"PK\x05\x06":
        raise SystemExit("unexpected ZIP comment or layout")
    cd_size, cd_off = struct.unpack("<II", apk[eocd + 12:eocd + 20])
    # With no signing block yet, the EOCD already points where the block will start.
    digest = chunked_digest([apk[:cd_off], apk[cd_off:cd_off + cd_size], apk[eocd:]])

    cert_der = cert.public_bytes(serialization.Encoding.DER)
    spki = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    signed_data = (lp(lp(struct.pack("<I", RSA_PKCS1_SHA256) + lp(digest)))
                   + lp(lp(cert_der))
                   + lp(b""))
    signature = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signer = lp(signed_data) + lp(lp(struct.pack("<I", RSA_PKCS1_SHA256) + lp(signature))) + lp(spki)
    value = lp(lp(signer))
    pairs = struct.pack("<QI", 4 + len(value), V2_BLOCK_ID) + value
    size = len(pairs) + 8 + 16
    block = struct.pack("<Q", size) + pairs + struct.pack("<Q", size) + b"APK Sig Block 42"

    tail = bytearray(apk[eocd:])
    struct.pack_into("<I", tail, 16, cd_off + len(block))
    return apk[:cd_off] + block + apk[cd_off:cd_off + cd_size] + bytes(tail)


def main():
    keystore, src, dst = sys.argv[1:4]
    password = os.environ["KEYSTORE_PASS"].encode()
    key, cert, _ = pkcs12.load_key_and_certificates(open(keystore, "rb").read(), password)

    tmp = dst + ".v1"
    with zipfile.ZipFile(src) as zin, open(tmp, "wb") as raw:
        zout = zipfile.ZipFile(raw, "w")
        copy_aligned(zin, raw, zout)
        add_v1(zin, zout, key, cert)
        zout.close()
    apk = open(tmp, "rb").read()
    os.remove(tmp)
    open(dst, "wb").write(add_v2(apk, key, cert))


if __name__ == "__main__":
    main()
