package com.sqasquared.toolkit.email;

import com.sqasquared.toolkit.UserSession;
import com.sqasquared.toolkit.connection.ASM;
import com.sqasquared.toolkit.connection.DataObject;
import com.sqasquared.toolkit.connection.RALLY;
import com.sqasquared.toolkit.connection.TaskRallyObject;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by jimmytran on 10/30/16.
 */
public class EmailGenerator {
    DataObject lastUpdatedEmail;

    public EmailGenerator() {

    }

    private Element mapListItem(String formattedId, String taskName, String
            estimate, Element listItemTemplate) {
        Element listItem = listItemTemplate.clone();
        Element fi = listItem.select("sqaas[type='formattedID']").first();
        fi.replaceWith(new TextNode(formattedId, ""));
        Element tn = listItem.select("sqaas[type='taskName']").first();
        tn.replaceWith(new TextNode(taskName, ""));
        Element eta = listItem.select("sqaas[type='ETA']").first();
        if (eta != null) {
            eta.replaceWith(new TextNode(estimate, ""));
            if (Double.parseDouble(estimate) > 1.0) {
                Element plural = listItem.select("sqaas[type='plural_s']")
                        .first();
                if (plural != null) {
                    plural.replaceWith(new TextNode("s", ""));
                }
            }
        }
        return listItem;
    }

    private Element mapStory(DataObject node, Element completedRoot, int
            order) {
        Element completed = completedRoot.clone();
        int countTasks = 0;
        String storyName = "";
        String storyLink = "";
        String storySubTag = "";
        String storyId = "";
        String storySubTagBracket = "";
        Element list = completed.select("ul").first();
        Element listItem = completed.select("sqaas[type='listItem']").first();
        for (DataObject st : node.getChildren().values()) {
            // loop over tasks
            if (st.getType().equals("task")) {
                TaskRallyObject task = (TaskRallyObject) st;
                if (storyName.equals("") || storyLink.equals("")) {
                    storyName = task.getStoryName().trim();
                    storyLink = task.getStoryLink();
                    storySubTag = task.getSubProjectTag(false);
                    storySubTagBracket = task.getSubProjectTag(true);
                    if(task.getStoryFormattedID() != null){
                        storyId = task.getStoryFormattedID();
                    }
                }
                Element listItemMapped = mapListItem(task.getFormattedID(),
                        task.getName(), task.getEstimate(), listItem);
                list.appendChild(listItemMapped);
            } else {
                throw new RuntimeException(
                        String.format("Wrong children node type. Expected %s," +
                                " got %s", "task", node.getType()));
            }
            countTasks++;
        }

        // remove order template
        if (order > 1) {
            completed.select("sqaas[type='first']").remove();
        } else {
            completed.select("sqaas[type='second']").remove();
        }

        // sub-project
        Element subProject = completed.select("sqaas[type='subProject'")
                .first();
        if (subProject != null) {
            subProject.replaceWith(new TextNode(storySubTag, ""));
        }

        // sub-project bracket
        Element subProjectBracket = completed.select
                ("sqaas[type='subProjectBracket'").first();
        if (subProjectBracket != null) {
            subProjectBracket.replaceWith(new TextNode(storySubTagBracket, ""));
        }

        // href links
        Element a = completed.select("a").first();
        if (a != null) {
            a.attr("href", storyLink);
        }

        // story name
        Element sn = completed.select("sqaas[type='storyName']").first();
        if (sn != null) {
            sn.replaceWith(new TextNode(storyName, ""));
        }

        // story id
        Element si = completed.select("sqaas[type='storyId']").first();
        if (si != null) {
            si.replaceWith(new TextNode(storyId, ""));
        }

        // task_plural
        Element tp = completed.select("sqaas[type='plural_s']").first();
        if (tp != null) {
            if (countTasks > 1) {
                tp.replaceWith(new TextNode("s", ""));
            }
        }

        // delete the list item template
        listItem.remove();

        return completed;
    }

    private void mapStory(DataObject node, Element completed) {
        int order = 1;
        for (DataObject story : node.getChildren().values()) {
            if (story.getType().equals("story")) {
                Element completedMapped = mapStory(story, completed, order);
                completed.before(completedMapped);
            } else {
                throw new RuntimeException(
                        String.format("Wrong children node type. Expected %s," +
                                " got %s", "story", story.getType()));
            }
            order++;
        }

        // remove template
        completed.remove();
    }

    private DataObject mapLastUpdatedStory(DataObject node, Element completed) {
        TaskRallyObject latestTask = null;
        DataObject latestStory = null;
        // Loop stories
        for (DataObject story : node.getChildren().values()) {
            if (story.getType().equals("story")) {
                // Loop tasks
                for (DataObject st : story.getChildren().values()) {
                    if (st.getType().equals("task")) {
                        TaskRallyObject task = (TaskRallyObject) st;
                        if (latestTask == null || latestStory == null) {
                            latestTask = task;
                            latestStory = story;
                        } else if (latestTask.getLastUpdateDate().before(task
                                .getLastUpdateDate())) {
                            latestTask = task;
                            latestStory = story;
                        }
                    } else {
                        throw new RuntimeException(
                                String.format("Wrong children node type. " +
                                        "Expected %s, got %s", "task", node
                                        .getType()));
                    }
                }
            } else {
                throw new RuntimeException(
                        String.format("Wrong children node type. Expected %s," +
                                " got %s", "story", story.getType()));
            }
        }
        Element mapLatestStory = mapStory(latestStory, completed, 1);
        completed.before(mapLatestStory);
        completed.remove();
        return latestStory;
    }

    private String generateSubject(DataObject ro) {
        String subject = UserSession.SSU_TAG;
        TaskRallyObject task = (TaskRallyObject) ro.getChildren().values()
                .iterator().next();
        if (task.getType().equals("task")) {
            String storyName = task.getStoryName();
            int i = storyName.lastIndexOf("] ");
            String tags = storyName.substring(0, i + 1);
            String base = storyName.substring(i + 1, storyName.length());
            subject += (" " + tags + " " + task.getStoryFormattedID() + base);
        } else {
            throw new RuntimeException(
                    String.format("Wrong children node type. Expected %s, got" +
                            " %s", "task", task.getType()));
        }
        return subject;
    }

    public String getLastEmailSubject() {
        return generateSubject(lastUpdatedEmail);
    }

    public String generate(DataObject topNode, String template) throws
            EmailGeneratorException {
        String htmlEmailTemplate = UserSession.getTemplate(template);
        Document doc = Jsoup.parse(htmlEmailTemplate);
        if (template.equals(UserSession.SSU) || template.equals(UserSession.SSUP)) {
            //All story status updates
            if (template.equals(UserSession.SSUP)) {
                // Story status update progress
                Element completed = doc.select("sqaas[type='completed']")
                        .first();
                DataObject completedNode = topNode.getChildren().get("today")
                        .getChildren().get(RALLY.COMPLETED);
                if (!completedNode.isEmpty()) {
                    mapLastUpdatedStory(completedNode, completed);
                } else {
                    throw new EmailGeneratorException("No completed tasks " +
                            "today. Get to work!!");
                }
            }

            // story status updates
            Element inProgress = doc.select("sqaas[type='notCompleted']")
                    .first();


            // use today's in-progress task
            DataObject inProgressNode = topNode.getChildren().get("today")
                    .getChildren().get(RALLY.INPROGRESS);
            if (!inProgressNode.isEmpty()) {
                lastUpdatedEmail = mapLastUpdatedStory(inProgressNode, inProgress);
            } else {
                // Use yesterday's in-progress task
                DataObject pastInProgressNode = topNode.getChildren().get
                        ("past")
                        .getChildren().get(RALLY.INPROGRESS);
                if (!pastInProgressNode.isEmpty()) {
                    lastUpdatedEmail = mapLastUpdatedStory
                            (pastInProgressNode, inProgress);
                } else {
                    throw new EmailGeneratorException("No in-progress tasks " +
                            "today. Get to work!!");
                }
            }
        } else {
            Element completed = doc.select("sqaas[type='completed']").first();
            // End of day
            DataObject completedNode = topNode.getChildren().get("today")
                    .getChildren().get(RALLY.COMPLETED);
            if (!completedNode.isEmpty()) {
                mapStory(completedNode, completed);
            } else {
                throw new EmailGeneratorException("No completed tasks today. " +
                        "Get to work!!");
            }

            Element notCompleted = doc.select("sqaas[type='notCompleted']")
                    .first();
            DataObject notCompletedNode = topNode.getChildren().get("today")
                    .getChildren().get(RALLY.DEFINED);
            if (!notCompletedNode.isEmpty()) {
                mapStory(notCompletedNode, notCompleted);
            } else {
                notCompletedNode = topNode.getChildren().get("today")
                        .getChildren().get(RALLY.INPROGRESS);
                if (!notCompletedNode.isEmpty()) {
                    mapStory(notCompletedNode, notCompleted);
                } else {
                    throw new EmailGeneratorException("No in-progress or " +
                            "declared tasks today");
                }
            }
        }


        // Map full name
        Element fullName = doc.select("sqaas[type='fullName']").first();
        if (fullName != null) {
            fullName.replaceWith(new TextNode(UserSession.getFullName(), ""));
        }

        return doc.toString();
    }

    public void saveEmail(String to, String cc, String subject, String
            html, String from, String loc) throws EmailException,
            IOException, MessagingException {
        HtmlEmail email = buildEmail(to, cc, subject, html, from);
        email.buildMimeMessage();
        MimeMessage mimeMessage = email.getMimeMessage();
        FileOutputStream fos = new FileOutputStream(new File(loc));
        mimeMessage.writeTo(fos);
        fos.flush();
        fos.close();
    }

    public HtmlEmail buildEmail(String to, String cc, String subject, String
            html, String from) throws EmailException,
            IOException, MessagingException {
        HtmlEmail email = new HtmlEmail();
        email.setHostName("secure.emailsrvr.com");
        email.setSmtpPort(993);
//        email.setSslSmtpPort("465");
        email.addTo(to.split(UserSession.EMAIL_SEPARATOR));
        email.setFrom(from);
        String[] ccs = cc.split(UserSession.EMAIL_SEPARATOR);
        if (ccs.length > 0 && ccs[0].length() > 0) {
            email.addCc(ccs);
        }
        email.setSubject(subject);
        email.setHtmlMsg(html);
        return email;
    }

    public void sendEmail(String to, String cc, String subject, String
            html, String from, String username, String password) throws EmailException,
            IOException, MessagingException {
        HtmlEmail email = buildEmail(to, cc, subject, html, from);
        email.setAuthentication(username, password);
        email.setSSLOnConnect(true);
        email.send();
    }


    private Element mapRowItem(String tfs_id, String tfs_name, Element rowItemTemplate) {
        Element rowItem = rowItemTemplate.clone();
        Element fi = rowItem.select("sqaas[type='tfs_id']").first();
        fi.replaceWith(new TextNode(tfs_id, ""));
        Element tn = rowItem.select("sqaas[type='tfs_name']").first();
        tn.replaceWith(new TextNode(tfs_name, ""));
        Element a = rowItem.select("a").first();
        if (a != null) {
            a.attr("href", ASM.INSTEP1_LINK + tfs_id);
        }
        return rowItem;
    }

    public String generateTestCase(DataObject top, String template) {
        String htmlEmailTemplate = UserSession.getTemplate(template);
        Document doc = Jsoup.parse(htmlEmailTemplate);
        Element row = doc.select("tr").get(1);
        for (DataObject obj : top.getChildren().values()) {
            Element rowItem = mapRowItem(obj.getId(), obj.getName(), row);
            row.after(rowItem);
        }

        // project name
        Element sp = doc.select("sqaas[type='subProject']").first();
        if (sp != null) {
            sp.replaceWith(new TextNode("SUBPROJECT", ""));
        }

        // top node link
        Element a = doc.select("a").first();
        if (a != null) {
            a.attr("href", ASM.INSTEP1_LINK + top.getId());
        }

        // top node type
        Element tnt = doc.select("sqaas[type='topNodeType']").first();
        if (tnt != null) {
            tnt.replaceWith(new TextNode(top.getType(), ""));
        }

        // top node type
        Element tni = doc.select("sqaas[type='topNodeId']").first();
        if (tni != null) {
            tni.replaceWith(new TextNode(top.getId(), ""));
        }

        // top node name
        Element tnn = doc.select("sqaas[type='topNodeName']").first();
        if (tnn != null) {
            tnn.replaceWith(new TextNode(top.getName(), ""));
        }


        row.remove();

        return doc.toString();
    }
}
