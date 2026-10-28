# Oqteris Security & Digital Signature Trust Policy

## 1. Executive Security Mandate

In enterprise document workflows, digital signatures protect legal, regulatory, and financial integrity. A common failure in naive PDF viewers is confusing **mathematical signature validity** with **identity trust**:
> *Anyone can generate a mathematically flawless self-signed RSA-4096 signature claiming to be "Chief Justice of the Supreme Court" or "CEO of Acme Corporation". If a viewer displays a green checkmark merely because the cryptographic hash matches the public key, users are vulnerable to impersonation and fraud.*

**Oqteris enforces a strict, uncompromising security model**:
1. **Mathematical Cryptographic Validity** is strictly separated from **Trust Anchor Verification**.
2. **The Green Checkmark is Never Granted Solely for Cryptographic Integrity.**
3. A signature receives trusted status **only** when its certificate chain resolves to an explicitly trusted root certificate in the configured trust store.
4. Security warnings are never concealed, bypassed, or silenced.

---

## 2. Digital Signature Status Hierarchy

When a user opens a signed PDF document, Oqteris evaluates the signature dictionary and assigns one of four verification states:

| Status Badge | Icon | Cryptographic Integrity | Certificate Trust | Meaning to the User |
|---|:---:|:---:|:---:|---|
| **VALID_AND_TRUSTED** | :white_check_mark: Green Check | Authentic & Untampered | Trusted Root Anchor | **Full Trust**: Document is unmodified since signing, and signer identity is certified by a trusted authority. |
| **VALID_BUT_UNTRUSTED** | :warning: Yellow Warning | Authentic & Untampered | Untrusted / Unknown | **Authentic but Unverified**: Signature is mathematically valid and document is untampered, but the issuer is not in the trusted certificates store. |
| **EXPIRED_CERTIFICATE** | :warning: Yellow Warning | Authentic & Untampered | Expired Validity Period | **Expired Credentials**: Certificate was valid at signing time but is currently expired, or signing timestamp is outside validity bounds. |
| **MODIFIED_AFTER_SIGNING** | :x: Red Alert | Altered / Appended | N/A | **Tampering Detected**: Content was modified or appended to the document after the signature was applied. Byte ranges do not cover whole document. |
| **INVALID_SIGNATURE** | :x: Red Alert | Corrupted / Invalid Hash | N/A | **Cryptographic Failure**: Computed document hash does not match decrypted signature digest. Signature is forged or corrupted. |

---

## 3. Cryptographic Verification Pipeline

The verification engine (`org.originsd.oqteris.crypto.signature.PdfSignatureVerifier`) performs a three-stage verification on every signed PDF:

```
[ Stage 1: Byte-Range Tamper Detection ]
       |
       +--> Computes SHA-256 over byte intervals [offset0, len0] & [offset1, len1]
       +--> Verifies byte range covers from byte 0 to the exact end of file (EOF)
       +--> If EOF > (offset1 + len1) ==> Status: MODIFIED_AFTER_SIGNING (RED)
       |
[ Stage 2: CMS / PKCS#7 Cryptographic Check ]
       |
       +--> Parses DER-encoded PKCS#7 / CMS SignedData container from /Contents
       +--> Validates signer public key against signed message digest
       +--> If hash decrypt fails ==> Status: INVALID_SIGNATURE (RED)
       |
[ Stage 3: X.509 Certificate Chain & Trust Store Validation ]
       |
       +--> Inspects Certificate notBefore & notAfter timestamps
       +--> If current time outside range ==> Status: EXPIRED_CERTIFICATE (YELLOW)
       +--> Evaluates certificate chain against TrustStoreManager (PKCS#12)
       +--> If not in trust store ==> Status: VALID_BUT_UNTRUSTED (YELLOW)
       +--> If matches trusted root in store ==> Status: VALID_AND_TRUSTED (GREEN)
```

---

## 4. Trust Store Architecture & Certificate Manager

### 4.1 Application-Managed PKCS#12 Trust Store
Oqteris maintains an independent, application-managed keystore located at:
- **Windows**: `%USERPROFILE%\.oqteris\truststore.p12`
- **Linux**: `$HOME/.oqteris/truststore.p12`

The trust store is managed by `TrustStoreManager` (`org.originsd.oqteris.crypto.trust.TrustStoreManager`), which provides:
- Password-encrypted persistence using industry-standard PKCS#12.
- Secure certificate storage with alias identifiers, SHA-256 fingerprints, import dates, and user comments.
- Trust levels:
  - `TRUSTED`: Certificate acts as a trusted root anchor for signature verification.
  - `UNTRUSTED`: Certificate is untrusted or revoked.

### 4.2 Operating System Trust Store Integration
Oqteris can optionally query the operating system's native certificate store:
- **Windows**: Windows-ROOT (`KeyStore.getInstance("Windows-ROOT")`) containing certificates in the Windows Certificate Manager (Local Machine / Current User).
- **Linux**: System CA bundle typically located at `/etc/ssl/certs/ca-certificates.crt`.

Users can toggle OS trust store integration in **Tools &rarr; Settings &rarr; Trust Store**.

### 4.3 Certificate Manager UI
Users and system administrators can inspect and manage certificates directly inside Oqteris via **Security &rarr; Certificate Manager** (`Ctrl+Shift+C`):
- **Inspect Signer Details**: View Subject DN, Issuer DN, Serial Number, Signature Algorithm, SHA-256 Fingerprint, and Validity Dates.
- **Import Trusted Certificate**: Import X.509 certificates (`.cer`, `.crt`, `.pem`) into the application trust store to certify new partners or corporate CAs.
- **Revoke / Remove**: Remove certificates from the trust store to instantly revoke trusted status across all documents.
- **Direct Trust from PDF**: When viewing a document with a valid but untrusted signature, clicking **View Signer & Trust Details** allows inspecting the certificate and promoting it to the trust store with explicit confirmation.

---

## 5. Security Best Practices

1. **Never Accept Red Badges**: Documents flagged with `MODIFIED_AFTER_SIGNING` or `INVALID_SIGNATURE` must be rejected immediately.
2. **Audit Yellow Badges**: Verify the signer's identity and certificate fingerprint through an out-of-band channel (e.g. phone confirmation, corporate directory) before importing a self-signed certificate into the trust store.
3. **Backup the Trust Store**: Regularly back up `~/.oqteris/truststore.p12` to preserve organizational trust decisions.
4. **VBA & Macro Disarming**: Oqteris deliberately does not execute VBA macros or Word basic scripts embedded in Office files, eliminating the most common vector for desktop malware infections.
