package net.unicon.lti.model.lti.dto;

import jakarta.servlet.http.HttpServletRequest;


public class DeepLinkDTO {

    private String toolLinkId;
    private String title;
    private String description;
    private Boolean assignment;
    private Float maxGrade;


    public DeepLinkDTO() {//Empty on purpose
    }

    public DeepLinkDTO(String toolLinkId, String title, String description) {
        this.toolLinkId = toolLinkId;
        this.title = title;
        this.description = description;
    }

    public DeepLinkDTO(String toolLinkId, String title, String description, Boolean assignment, Float maxGrade) {
        this(toolLinkId, title, description);
        this.assignment = assignment;
        this.maxGrade = maxGrade;
    }

    public DeepLinkDTO(HttpServletRequest req) {

        this(req.getParameter("toolLinkId"), req.getParameter("title"), req.getParameter("description")
        );
    }

    public String getToolLinkId() {
        return toolLinkId;
    }

    public void setToolLinkId(String toolLinkId) {
        this.toolLinkId = toolLinkId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getAssignment() {
        return assignment;
    }

    public void setAssignment(Boolean assignment) {
        this.assignment = assignment;
    }

    public Float getMaxGrade() {
        return maxGrade;
    }

    public void setMaxGrade(Float maxGrade) {
        this.maxGrade = maxGrade;
    }
}
