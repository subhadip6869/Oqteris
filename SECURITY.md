# Oqteris Security Policy & Trust Architecture

## 1. Vulnerability Reporting & Disclosure

We take the security of Oqteris seriously. If you discover or suspect a security vulnerability, **please do not disclose it publicly in an issue or discussion.**

- **Preferred Disclosure Channel**: Use GitHub's **Private Vulnerability Reporting** feature located under the repository's **Security** tab (`Security` -> `Advisories` -> `Report a vulnerability`).
- **Response Timeline**: Security reports are acknowledged within 48 hours with a preliminary assessment.
- **Coordination**: We ask you to follow responsible disclosure guidelines and allow time for a patch and release before public disclosure.

---

## 2. Executive Security Mandate

In enterprise document workflows, digital signatures protect legal, regulatory, and financial integrity. A common failure in naive PDF viewers is confusing **mathematical signature validity** with **identity trust**:
> *Anyone can generate a mathematically flawless self-signed RSA-4096 signature claiming to be "Chief Justice of the Supreme Court" or "CEO of Acme Corporation". If a viewer displays a green checkmark merely because the cryptographic hash matches the public key, users are vulnerable to impersonation and fraud.*

**Oqteris enforces a strict, uncompromising security model**:
1. **Mathematical Cryptographic Validity** is strictly separated from **Trust Anchor Verification**.
2. **The Green Checkmark is Never Granted Solely for Cryptographic Integrity.**
3. A signature receives trusted status **only** when its certificate chain resolves to an explicitly trusted root certificate in the configured trust store.
4. Security warnings are never concealed, bypassed, or silenced.
5. **Macro Disarming**: Oqteris deliberately does not execute VBA macros or embedded scripts in any Office format, eliminating the primary vector for desktop malware infections via documents.

---

## 3. Digital Signature Status Hierarchy

When a user opens a signed PDF document, Oqteris evaluates the signature dictionary and assigns one of five verification states:

| Status | Badge | Color | Cryptographic Integrity | Certificate Trust | Meaning |
|---|:---:|:---:|:---:|:---:|---|
| `VALID_AND_TRUSTED` | ✓ Shield Check | 🟢 Green | Valid & Untampered | Trusted Root Anchor | **Full Trust**: Document is unmodified since signing and signer identity is certified by a trusted authority. |
| `VALID_BUT_UNTRUSTED` | ? Shield Alert | 🟡 Yellow | Valid & Untampered | Not in trust store | **Authentic but Unverified**: Signature is cryptographically intact, but the signer's certificate is not anchored to any trusted root. |
| `CERTIFICATE_EXPIRED` | ✕ Shield Off | 🔴 Red | Valid & Untampered | Expired / Not Yet Valid | **Expired Credentials**: Certificate was expired or not yet valid at the time of signing or verification. |
| `DOCUMENT_MODIFIED` | ✕ Shield Off | 🔴 Red | Byte ranges altered | N/A | **Tampering Detected**: Content was modified or appended to the document after the signature was applied. Byte range does not extend to end-of-file. |
| `INVALID_SIGNATURE` | ✕ Shield Off | 🔴 Red | Corrupted / Hash Mismatch | N/A | **Cryptographic Failure**: Computed document hash does not match decrypted signature digest. Signature is forged or corrupted. |

> **Important**: `CERTIFICATE_EXPIRED` is a **red** (rejection) status. An expired certificate means the signing credentials cannot be validated with confidence; such documents should not be accepted as fully trusted.

---

## 4. Cryptographic Verification Pipeline

The verification engine (`org.originsd.oqteris.crypto.signature.PdfSignatureVerifier`) performs a four-stage pipeline on every signed PDF:

```
[ Stage 1: Byte-Range Tamper Detection ]
       |
       +--> Reads all signature dictionaries from the PDF via PDFBox
       +--> Extracts the byte range: [offset0, len0, offset1, len1]
       +--> Verifies start1 == 0 AND (start2 + len2) >= fileLength
       +--> If byte range does not cover EOF ==> Status: DOCUMENT_MODIFIED (RED)
       |
[ Stage 2: CMS / PKCS#7 Cryptographic Check ]
       |
       +--> Parses DER-encoded CMS SignedData container from /Contents
       +--> Reconstructs the signed byte content (excluding the /Contents hex block)
       +--> Locates the primary SignerInformation and matching X.509 certificate
       +--> Verifies signer public key against the CMS signed message digest
       +--> If verification fails ==> Status: INVALID_SIGNATURE (RED)
       |
[ Stage 3: Signing Time Resolution ]
       |
       +--> Reads signingTime from CMS signed attributes (local clock fallback)
       +--> Checks unauthenticated attributes for RFC 3161 TimeStampToken (TSP)
       +--> If a valid TSP token is present, its timestamp takes precedence
       +--> TSP presence is recorded on PdfSignatureInfo (shown as "Timestamped" in UI)
       |
[ Stage 4: X.509 Certificate Chain & Trust Store Validation ]
       |
       +--> Builds certificate chain from leaf signer up through embedded intermediates
       +--> Checks certificate validity periods against resolved signing time
       +--> If any cert in chain is expired/not-yet-valid ==> Status: CERTIFICATE_EXPIRED (RED)
       +--> Checks explicit blacklist (UNTRUSTED entries in app trust store)
       +--> If blacklisted ==> Status: CERTIFICATE_EXPIRED / rejected (RED)
       +--> Walks chain from root to leaf, looking for anchor in TrustStoreManager
       +--> Checks custom app trust store (PKCS#12) first, then system roots (if enabled)
       +--> If trust anchor found ==> Status: VALID_AND_TRUSTED (GREEN)
       +--> If no anchor found ==> Status: VALID_BUT_UNTRUSTED (YELLOW)
```

### Multi-Signature Support

Oqteris verifies **all** embedded signatures in a PDF document, not just the first one. Each signature is evaluated independently and displayed as a separate card in the **Digital Signatures** sidebar panel.

### Visual Signature Appearance Rewriting

Many signed PDFs (produced by iText, OpenPDF, or Acrobat 5/6-layer style) embed a layered appearance with XObjects named `n0`–`n4`. By default, PDF viewers such as PDFBox display the stored (pre-verification) state — typically a yellow question mark, even for trusted signatures.

Oqteris implements `SignatureValidityAppearance`, which rewrites these layers **in memory** (never touching the file on disk) to show the correct verification result:
- **`n1` layer**: Green tick (trusted), yellow "?" (untrusted), or blank (invalid).
- **`n3` layer**: Red cross (invalid), or blank (valid/unknown).
- **`n4` layer**: Caption text — "Signature valid", "Validity unknown", or "Signature invalid".

The rewriting supports layer hierarchies up to two levels deep (e.g., `/FRM` wrapper used by iText).

---

## 5. Trust Store Architecture & Certificate Manager

### 5.1 Application-Managed PKCS#12 Trust Store

Oqteris maintains an independent, application-managed keystore at:
- **Windows**: `%USERPROFILE%\.oqteris\truststore.p12`
- **Linux / macOS**: `$HOME/.oqteris/truststore.p12`
- **Fallback** (if `user.home` is not writable): `<java.io.tmpdir>/.oqteris/truststore.p12`

The trust store is managed by `TrustStoreManager` (`org.originsd.oqteris.crypto.trust.TrustStoreManager`), a thread-safe singleton that provides:
- Password-encrypted persistence using industry-standard PKCS#12.
- Certificate storage with alias identifiers, SHA-256 and SHA-1 fingerprints, import dates, and user notes.
- **Trust levels** (defined in `TrustLevel`):
  - `TRUSTED` (`#28a745`): Certificate acts as a trusted root anchor for signature verification.
  - `UNTRUSTED` (`#dc3545`): Certificate is explicitly blacklisted / revoked — any signature chaining to it will be rejected.
  - `UNKNOWN` (`#ffc107`): Certificate requires user confirmation before trust is extended.

### 5.2 Operating System Trust Store Integration

Oqteris can optionally query the operating system's native certificate store:
- **JRE cacerts**: Loaded from `$JAVA_HOME/lib/security/cacerts` using the standard `changeit` password.
- **Windows**: `Windows-ROOT` keystore (`KeyStore.getInstance("Windows-ROOT")`) containing certificates in the Windows Certificate Manager (Local Machine / Current User).
- **Linux**: System CA bundle loaded through the JRE cacerts (platform-specific path).

Users can toggle OS trust store integration in **Tools → Settings → Trust Store**.

### 5.3 Certificate Chain Building

The verifier constructs the full certification path from the leaf signer certificate upward through any intermediate CA certificates embedded in the CMS `SignedData` structure. Chain building:
1. Starts from the signer certificate.
2. At each step, searches the pool of embedded certificates for one whose `SubjectDN` matches the current certificate's `IssuerDN` **and** whose public key passes `current.verify(issuer.publicKey)`.
3. Stops when a self-signed certificate is reached or no further issuer is found.

Trust evaluation then walks the chain **from root to leaf**, stopping as soon as a trusted anchor is found in the application store or system roots.

### 5.4 Certificate Manager UI

Users and system administrators can inspect and manage certificates directly inside Oqteris via **Security → Certificate Manager** (`Ctrl+Shift+C`):
- **Inspect Signer Details**: View Subject DN, Issuer DN, Serial Number, Signature Algorithm, Public Key (RSA key size), SHA-256 Fingerprint, SHA-1 Fingerprint, Key Usages, and Validity Dates.
- **Certificate Chain Tree**: Hierarchical view of the full certification path from root CA down to the leaf signer, with `[CA]` annotations for certificate authorities.
- **Import Trusted Certificate**: Import X.509 certificates (`.cer`, `.crt`, `.pem`) into the application trust store to certify new partners or corporate CAs.
- **Revoke / Remove**: Remove certificates from the trust store to instantly revoke trusted status across all documents.
- **Direct Trust from PDF**: When viewing a document with a `VALID_BUT_UNTRUSTED` signature, clicking **Trust Signer...** in the Signatures panel prompts the user for confirmation, then imports the signer's certificate into the trust store and immediately re-verifies all signatures in the open document.

---

## 6. Certificate Inspection Details

The `CertificateDetails` class (`org.originsd.oqteris.crypto.trust.CertificateDetails`) exposes the following attributes for every X.509 certificate in the chain:

| Attribute | Description |
|---|---|
| Subject DN | Full Distinguished Name of the certificate subject |
| Issuer DN | Full Distinguished Name of the certificate issuer |
| Serial Number | Certificate serial number in hexadecimal (uppercase) |
| Valid From | `notBefore` timestamp |
| Valid Until | `notAfter` timestamp |
| Signature Algorithm | e.g. `SHA256withRSA` |
| Public Key | Algorithm and key size, e.g. `RSA 2048 bits` |
| SHA-256 Fingerprint | Hex-encoded SHA-256 digest of the DER-encoded certificate |
| SHA-1 Fingerprint | Hex-encoded SHA-1 digest of the DER-encoded certificate |
| Key Usages | e.g. `Digital Signature`, `Non-Repudiation`, `Key Certificate Sign` |
| Is CA | Whether the certificate has Basic Constraints with `cA=TRUE` |

---

## 7. RFC 3161 Timestamp Token (TSP) Support

Oqteris detects and parses **RFC 3161 Trusted Timestamp Tokens** embedded in the unauthenticated attributes of a CMS signature (`id-aa-signatureTimeStampToken` OID). When present:

- The TSP token's `genTime` is used as the authoritative signing time for certificate validity checks, instead of the local `signingTime` attribute or the PDF signing date.
- The UI displays the signing date with a **(Timestamped)** label, indicating the time was independently certified by a Timestamp Authority (TSA).
- TSP presence is recorded in `PdfSignatureInfo.hasTimestampToken()` and `getTimestampTime()`.

---

## 8. Security Best Practices

1. **Never Accept Red Badges**: Documents flagged with `DOCUMENT_MODIFIED`, `INVALID_SIGNATURE`, or `CERTIFICATE_EXPIRED` must be rejected. Do not rely on document content when any red badge is displayed.
2. **Audit Yellow Badges**: `VALID_BUT_UNTRUSTED` means the signature is cryptographically intact but the signer's identity is unverified. Confirm the signer's identity and certificate fingerprint through an out-of-band channel (phone, corporate directory) before importing the certificate into the trust store.
3. **Prefer Certificate Chain Trust**: When importing a certificate, prefer importing the **CA root certificate** rather than the individual signer certificate, so all documents signed by that CA are automatically trusted.
4. **Backup the Trust Store**: Regularly back up `~/.oqteris/truststore.p12` to preserve organizational trust decisions across machines and reinstalls.
5. **Use TSP-Timestamped Signatures**: Signatures with an embedded RFC 3161 timestamp token (shown as "Timestamped") are more reliable because the signing time was independently certified, preventing backdating attacks.
6. **Blacklist Compromised Certificates**: If a signing certificate is known to be compromised, import it with `TrustLevel.UNTRUSTED` to immediately block all documents signed with that certificate, even if they would otherwise verify correctly.
