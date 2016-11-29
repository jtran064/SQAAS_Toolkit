package com.sqasquared.toolkit;

import com.sqasquared.toolkit.connection.DataObject;
import com.sqasquared.toolkit.connection.TaskRallyObject;
import com.sqasquared.toolkit.email.EmailGenerator;
import com.sqasquared.toolkit.email.EmailGeneratorException;
import org.apache.commons.mail.EmailException;

import javax.mail.MessagingException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.prefs.Preferences;

/**
 * Created by jimmytran on 10/30/16.
 */
public class UserSession {

    public static final String EOD = "end_of_day";
    public static final String SSU = "story_status_update";
    public static final String SSU_TAG = "[STORY STATUS UPDATE]";
    public static final String EOD_TAG = "[END OF DAY UPDATE]";
    private static final String SSU_KEY = "SSU";
    private static final String EOD_KEY = "EOD";
    private static final String TO = "to";
    private static final String CC = "cc";
    private static final String SEPARATOR = "_";
    public static final String EMAIL_SEPARATOR = ",";
    public static Date TODAY_WORK_HOUR;
    public static Date YESTERDAY_WORK_HOUR;
    private static Preferences prop;
    private TreeAlgorithmInterface alg;
    private HashMap<String, DataObject> taskContainer = new HashMap();
    private final HashMap<String, String> templateContainer = new HashMap();
    private DataObject topNode = null;
    private final Loader loader;
    private final EmailGenerator gen = new EmailGenerator();

    public UserSession() {
        Calendar today = Calendar.getInstance();
        today.clear(Calendar.MINUTE);
        today.clear(Calendar.SECOND);
        today.clear(Calendar.MILLISECOND);
        today.set(Calendar.HOUR_OF_DAY, 6);
        TODAY_WORK_HOUR = today.getTime();
        today.add(Calendar.DATE, -1);
        YESTERDAY_WORK_HOUR = today.getTime();

        loadPreferences();
        setAlg(new TimeAlgorithm());
        loader = new Loader();
    }

    public static String getProperty(String property) {
        String val = prop.get(property, "");
        if (val.equals("")) {
            System.err.println("Unset property: " + property);
        }
        return val;
    }

    private void loadPreferences() {
        prop = Preferences.userNodeForPackage(UserSession.class);

        if (prop.getBoolean("first", true)) {
            prop.putBoolean("first", false);
            prop.put("user", "");
            prop.put("api_key", "");
            prop.put("server", "https://rally1.rallydev.com");
            prop.put("cc", "sqaas@sqasquared.com");
            prop.put("DEFAULT_to", "sqaas@sqasquared.com");
            prop.put("ASM_EOD_to", "jramos@sqasquared.com,abyrum@sqasquared.com,jdeleon@sqasquared.com");
            prop.put("ASM_SSU_to", "seth.labrum@advantagesolutions.net,patricia.liu@advantagesolutions.net," +
                    "joel.ramos@advantagesolutions.net,lynnyrd.raymundo@advantagesolutions.net");
        } else {
//            try {
//                String[] keys = prop.keys();
//                for (int i = 0; i < keys.length; i++) {
//                    System.out.println(keys[i] + " = " + prop.get(keys[i], ""));
//                }
//            } catch (BackingStoreException e) {
//                e.printStackTrace();
//            }
        }
    }

    public void refreshTasks() throws IOException {
        taskContainer = new HashMap<String,DataObject>();
        loader.loadTasks(this);
        loader.loadUserStory(this);
        run();
    }

    private void run() {
        topNode = this.alg.constructTree(taskContainer);
    }

    public void setProperty(String property, String value) {
        prop.put(property, value);
    }

    public boolean isUserPreferencesValid() {
        return !(prop.get("firstName", "").equals("") || prop.get("lastName", "").equals("")
                || prop.get("email", "").equals(""));
    }

    public boolean isAPIKeySet() {
        System.out.println(prop.get("api_key", ""));
        return !prop.get("api_key", "").equals("");
    }

    public void setAPIKey(String value) {
        prop.put("api_key", value);
    }

    public String getEmailTo(String emailType) {
        String key = null;
        if (emailType.equals(EOD)) {
            key = EOD_KEY;
        } else if (emailType.equals(SSU)) {
            key = SSU_KEY;
        }
        String keyTo = formatKey(getBusinessPartner(), key, TO);
//        String emailTo = getProperty(keyTo);
//        String[] emails = emailTo.split(EMAIL_SEPARATOR);
//        return emails;
        return getProperty(keyTo);
    }

    public String getEmailCC() {
        String emailTo = getProperty(CC);
//        String[] emails = emailTo.split(EMAIL_SEPARATOR);
//        return emails;
        return emailTo;
    }

    private String getBusinessPartner() {
        // If no business partners, default to ASM
        String bp = getProperty("business_partner");
        if (bp != null && bp.length() != 0) {
            return bp;
        }
        return "ASM";
    }

    private String formatKey(String... str) {
        String formatted = "";
        for (String s : str) {
            if (formatted.equals("")) {
                formatted += s;
            } else {
                formatted += (SEPARATOR + s);
            }
        }
        return formatted;
    }

    public void generateEmail(String to, String cc, String subject, String html, String loc) throws EmailException, MessagingException, IOException {
        gen.createEmail(to, cc, subject, html, getEmail(), loc);
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        String newLine = System.getProperty("line.separator");

        result.append(this.getClass().getName());
        result.append(" Object {");
        result.append(newLine);

        //determine fields declared in this class only (no fields of superclass)
        Field[] fields = this.getClass().getDeclaredFields();

        //print field names paired with their values
        for (Field field : fields) {
            result.append("  ");
            try {
                result.append(field.getName());
                result.append(": ");
                //requires access to private field:
                result.append(field.get(this));
            } catch (IllegalAccessException ex) {
                ex.printStackTrace();
            }
            result.append(newLine);
        }
        result.append("}");

        return result.toString();
    }

    public String generateHtml(String template) throws EmailGeneratorException {
        run();
        return gen.generate(this, template);
    }

    public String getEmailSubject(String template) {
        return gen.getLastEmailSubject();
    }

    public String getFullName() {
        return getProperty("firstName") + " " + getProperty("lastName");
    }

    public void setFirstName(String firstName) {
        prop.put("firstName", firstName);
    }

    public void setLastName(String lastName) {
        prop.put("lastName", lastName);
    }

    public String getEmail() {
        return getProperty("email");
    }

    public void setEmail(String email) {
        prop.put("email", email);
    }

    public void addTask(TaskRallyObject task) {
        taskContainer.put(task.getFormattedID(), task);
    }

    public void addTemplate(String baseName, String template) {
        templateContainer.put(baseName, template);
    }

    public String getTemplate(String template) {
        return templateContainer.get(template);
    }

    private void setAlg(TreeAlgorithmInterface alg) {
        this.alg = alg;
    }

    public DataObject getTopNode() {
        return topNode;
    }

    public HashMap<String, DataObject> getTaskContainer() {
        return taskContainer;
    }
}
