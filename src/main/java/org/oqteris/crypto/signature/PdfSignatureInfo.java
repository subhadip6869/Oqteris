package org.oqteris.crypto.signature;

import org.oqteris.crypto.trust.CertificateDetails;
import org.oqteris.crypto.trust.TrustStoreManager;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Encapsulates the results of verifying a PDF digital signature.
 */
public class PdfSignatureInfo {
    private final String fieldName;
    private final String signerName;
    private final String signerDN;
    private final Date signingTime;
    private final String reason;
    private final String location;
    private final String filter;
    private final String subFilter;
    private final String digestAlgorithm;
    private final boolean cryptographicallyValid;
    private final boolean coversWholeDocument;
    private final long byteRangeLength;
    private final long totalFileLength;
    private final SignatureVerificationStatus status;
    private final String statusDetails;
    private final X509Certificate signerCertificate;
    private final List<X509Certificate> certificateChain;
    private final boolean hasTimestampToken;
    private final Date timestampTime;
    private int pageIndex = -1;
    private double[] visualRect = null; // [normX, normY, normW, normH] in [0.0..1.0] relative coordinates
    private boolean isVisual = false;

    public PdfSignatureInfo(String fieldName, String signerName, String signerDN, Date signingTime,
                            String reason, String location, String filter, String subFilter,
                            String digestAlgorithm, boolean cryptographicallyValid,
                            boolean coversWholeDocument, long byteRangeLength, long totalFileLength,
                            SignatureVerificationStatus status, String statusDetails,
                            X509Certificate signerCertificate, List<X509Certificate> certificateChain,
                            boolean hasTimestampToken, Date timestampTime) {
        this.fieldName = fieldName;
        this.signerName = signerName;
        this.signerDN = signerDN;
        this.signingTime = signingTime;
        this.reason = reason;
        this.location = location;
        this.filter = filter;
        this.subFilter = subFilter;
        this.digestAlgorithm = digestAlgorithm;
        this.cryptographicallyValid = cryptographicallyValid;
        this.coversWholeDocument = coversWholeDocument;
        this.byteRangeLength = byteRangeLength;
        this.totalFileLength = totalFileLength;
        this.status = status;
        this.statusDetails = statusDetails;
        this.signerCertificate = signerCertificate;
        this.certificateChain = certificateChain != null ? Collections.unmodifiableList(new ArrayList<>(certificateChain)) : Collections.emptyList();
        this.hasTimestampToken = hasTimestampToken;
        this.timestampTime = timestampTime;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getSignerName() {
        return signerName;
    }

    public String getSignerDN() {
        return signerDN;
    }

    public Date getSigningTime() {
        return signingTime;
    }

    public String getReason() {
        return reason;
    }

    public String getLocation() {
        return location;
    }

    public String getFilter() {
        return filter;
    }

    public String getSubFilter() {
        return subFilter;
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    public boolean isCryptographicallyValid() {
        return cryptographicallyValid;
    }

    public boolean coversWholeDocument() {
        return coversWholeDocument;
    }

    public long getByteRangeLength() {
        return byteRangeLength;
    }

    public long getTotalFileLength() {
        return totalFileLength;
    }

    public SignatureVerificationStatus getStatus() {
        return status;
    }

    public String getStatusDetails() {
        return statusDetails;
    }

    public X509Certificate getSignerCertificate() {
        return signerCertificate;
    }

    public List<X509Certificate> getCertificateChain() {
        return certificateChain;
    }

    public boolean hasTimestampToken() {
        return hasTimestampToken;
    }

    public Date getTimestampTime() {
        return timestampTime;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public double[] getVisualRect() {
        return visualRect;
    }

    public boolean isVisual() {
        return isVisual;
    }

    public void setVisualLocation(int pageIndex, double[] visualRect) {
        this.pageIndex = pageIndex;
        this.visualRect = visualRect;
        this.isVisual = (visualRect != null);
    }

    public CertificateDetails getSignerCertificateDetails() {
        return signerCertificate != null ? new CertificateDetails(signerCertificate) : null;
    }
}
