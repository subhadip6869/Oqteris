package org.originsd.oqteris.core.model;

import java.time.Instant;

/**
 * Common metadata attributes for documents in Oqteris.
 */
public class DocumentMetadata {
    private String title = "";
    private String author = System.getProperty("user.name", "Oqteris User");
    private String subject = "";
    private String keywords = "";
    private String comments = "";
    private Instant created = Instant.now();
    private Instant modified = Instant.now();
    private int revision = 1;

    public DocumentMetadata() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "";
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author != null ? author : "";
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject != null ? subject : "";
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords != null ? keywords : "";
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments != null ? comments : "";
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created != null ? created : Instant.now();
    }

    public Instant getModified() {
        return modified;
    }

    public void setModified(Instant modified) {
        this.modified = modified != null ? modified : Instant.now();
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = Math.max(1, revision);
    }
}
