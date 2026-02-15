/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2026 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package sc.fiji.snt.bridge;

import net.imagej.ImageJService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.net.URI;

/**
 * A Java 8-compiled SciJava {@link Service} that checks the running JVM version
 * at Fiji startup and warns users if the runtime is too old for the current SNT
 * release.
 * <p>
 * This bridge exists because the Fiji Updater has no mechanism to serve
 * different JARs based on the JVM version. When Java 21-compiled SNT artifacts
 * are uploaded to the Neuroanatomy update site, users on Java 8 will pull those
 * JARs but fail to load them ({@link UnsupportedClassVersionError}). This
 * Service intercepts that scenario by running <em>before</em> any SNT class is
 * loaded (SciJava discovers commands lazily from annotation indexes) and
 * presenting a more friendly upgrade dialog.
 * </p>
 * <p>
 * <b>Compilation requirement:</b> This JAR <em>must</em> be compiled with
 * {@code -source 1.8 -target 1.8} so that it loads on legacy Fiji
 * installations.
 * </p>
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Service.class)
public class JavaVersionBridge extends AbstractService implements ImageJService {

    /**
     * The minimum Java feature version required by the current SNT release.
     */
    static final int REQUIRED_JAVA_VERSION = 21;

    private static final String FIJI_RECOMMENDED_VERSION = "Fiji-Latest";
    private static final String RELEASE_NOTES_URL = "https://github.com/morphonets/SNT/releases";
    private static final String FIJI_DOWNLOAD_URL = "https://imagej.net/software/fiji/downloads";
    private static final String FORUM_URL = "https://forum.image.sc/tag/snt";
    private static final String UPDATE_SITE_NAME = "Neuroanatomy";

    @Parameter(required = false)
    private LogService log;

    /**
     * Parses the major Java version from {@code java.version}.
     * <ul>
     * <li>Java 8: {@code "1.8.0_xxx"} &rarr; 8</li>
     * <li>Java 9+: {@code "9"}, {@code "11.0.x"}, {@code "21.0.x"} &rarr; 9, 11, 21</li>
     * </ul>
     */
    static int getMajorJavaVersion() {
        final String prop = System.getProperty("java.version", "0");
        return parseMajorVersion(prop);
    }

    /**
     * Package-private for testing.
     */
    static int parseMajorVersion(final String versionString) {
        if (versionString == null || versionString.isEmpty()) {
            return 0;
        }
        try {
            // Java 8 and earlier: "1.x.y_z"
            if (versionString.startsWith("1.")) {
                final int dot = versionString.indexOf('.', 2);
                final String minor = (dot > 2) ? versionString.substring(2, dot) : versionString.substring(2);
                return Integer.parseInt(minor);
            }
            // Java 9+: "major.minor.patch" or just "major"
            final int dot = versionString.indexOf('.');
            final String major = (dot > 0) ? versionString.substring(0, dot) : versionString;
            // Strip any non-numeric suffix (e.g. "-ea")
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < major.length(); i++) {
                final char c = major.charAt(i);
                if (Character.isDigit(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
            return sb.length() > 0 ? Integer.parseInt(sb.toString()) : 0;
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private static void openURL(final String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (final Exception ignored) {
            // We tried: the URL is visible in the dialog for manual copy
        }
    }

    private static JEditorPane getEditorPane(int currentVersion) {
        final String html = "<html><body style='width:450px; font-family:sans-serif;'>"
                + "<h3>SNT Requires Java " + REQUIRED_JAVA_VERSION + "</h3>"
                + "<p>This Fiji installation is running <b>Java " + currentVersion
                + "</b>, but SNT now requires <b>Java " + REQUIRED_JAVA_VERSION
                + "</b> or newer. SNT commands will not work in this installation.</p>"
                + "<p>Newer SNT versions are dramatically improved in features, accuracy, "
                + "and performance. See the <a href='" + RELEASE_NOTES_URL + "'>Release Notes</a> for details.</p>"
                + "<p>To continue using SNT, download <b>" + FIJI_RECOMMENDED_VERSION + "</b> from "
                + "<a href='" + FIJI_DOWNLOAD_URL + "'>" + FIJI_DOWNLOAD_URL + "</a>, "
                + "then subscribe to the <b>Neuroanatomy</b> update site via "
                + "<i>Help &gt; Update... &gt; Manage update sites</i>.</p>"
                + "<p>Your existing data and traces are not affected. "
                + "Questions? Visit the <a href='" + FORUM_URL + "'>Image.sc forum</a>.</p>"
                + "</body></html>";
        final JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setOpaque(false);
        return pane;
    }

    @Override
    public void initialize() {
        final int current = getMajorJavaVersion();
        if (current >= REQUIRED_JAVA_VERSION) {
            return; // nothing to do
        }
        if (log != null) {
            log.warn("SNT requires Java " + REQUIRED_JAVA_VERSION +
                    " but this Fiji is running Java " + current + ". SNT commands will not function.");
        }
        if (isNeuroanatomySiteDeactivated()) {
            // User already chose to unsubscribe in a previous session.
            // Files remain on disk until the updater cleans them up.
            if (log != null) {
                log.info("Neuroanatomy update site is deactivated. Run the updater to remove leftover files.");
            }
            return;
        }
        if (!GraphicsEnvironment.isHeadless()) {
            // Delay display to avoid visual glitches during Fiji's startup
            // theme initialization (e.g., FlatLaf loading after the default L&F)
            final javax.swing.Timer timer = new javax.swing.Timer(2500, e -> showUpgradeDialog(current));
            timer.setRepeats(false);
            timer.start();
        }
    }

    /**
     * Checks whether the Neuroanatomy update site has already been deactivated
     * (e.g., by a previous invocation of this bridge). Uses reflection to avoid
     * compile-time dependency on the updater.
     */
    private boolean isNeuroanatomySiteDeactivated() {
        try {
            final String ijDir = System.getProperty("ij.dir");
            if (ijDir == null || ijDir.isEmpty()) return false;
            final Class<?> filesClass = Class.forName("net.imagej.updater.FilesCollection");
            Object files;
            try {
                files = filesClass.getConstructor(
                        Class.forName("org.scijava.log.LogService"), java.io.File.class
                ).newInstance(log, new java.io.File(ijDir));
            } catch (final NoSuchMethodException e) {
                files = filesClass.getConstructor(java.io.File.class)
                        .newInstance(new java.io.File(ijDir));
            }
            filesClass.getMethod("read").invoke(files);

            // Try both API signatures
            Object site;
            try {
                site = filesClass.getMethod("getUpdateSite", String.class, boolean.class)
                        .invoke(files, UPDATE_SITE_NAME, true); // true = include disabled
            } catch (final NoSuchMethodException e) {
                site = filesClass.getMethod("getUpdateSite", String.class)
                        .invoke(files, UPDATE_SITE_NAME);
            }
            if (site == null) return false;

            // Check site.isActive()
            try {
                return !((Boolean) site.getClass().getMethod("isActive").invoke(site));
            } catch (final NoSuchMethodException e) {
                // Try direct field
                final java.lang.reflect.Field f = site.getClass().getDeclaredField("active");
                f.setAccessible(true);
                return !f.getBoolean(site);
            }
        } catch (final Exception e) {
            // If we can't determine state, assume it's active and show the dialog
            return false;
        }
    }

    private void showUpgradeDialog(final int currentVersion) {
        final JEditorPane pane = getEditorPane(currentVersion);
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openURL(e.getURL().toString());
            }
        });
        final String UNSUBSCRIBE = "Unsubscribe from Neuroanatomy Update Site";
        final String KEEP_REMINDING = "Keep Reminding Me at Startup";
        final int choice = JOptionPane.showOptionDialog(null, pane,
                "SNT: Java Upgrade Required", // title
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, // icon
                new String[]{UNSUBSCRIBE, KEEP_REMINDING},
                KEEP_REMINDING);

        if (choice == 0) {
            unsubscribeFromNeuroanatomy();
        }
        // choice == 1 or closed: do nothing, dialog will reappear next launch
    }

    /**
     * Attempts to deactivate the Neuroanatomy update site programmatically and
     * then launches the Fiji Updater GUI so the user can review and apply the
     * resulting changes (downgrades, removals, etc.). Uses reflection to avoid
     * compile-time dependencies on {@code imagej-updater} and
     * {@code imagej-ui-swing}. Should handle both old (2.0.x) and newer API signatures.
     */
    private void unsubscribeFromNeuroanatomy() {
        try {
            // Locate Fiji installation directory
            final String ijDir = System.getProperty("ij.dir");
            if (ijDir == null || ijDir.isEmpty()) {
                showUnsubscribeFailure("Could not determine the Fiji installation directory.");
                return;
            }
            final java.io.File ijDirFile = new java.io.File(ijDir);
            // FilesCollection: try (LogService, File) constructor first, then (File)
            final Class<?> filesClass = Class.forName("net.imagej.updater.FilesCollection");
            Object files;
            try {
                files = filesClass.getConstructor(
                        Class.forName("org.scijava.log.LogService"), java.io.File.class
                ).newInstance(log, ijDirFile);
            } catch (final NoSuchMethodException e) {
                files = filesClass.getConstructor(java.io.File.class).newInstance(ijDirFile);
            }
            // files.read()
            filesClass.getMethod("read").invoke(files);
            // getUpdateSite: try (String, boolean) first, then (String)
            Object site;
            try {
                site = filesClass.getMethod("getUpdateSite", String.class, boolean.class)
                        .invoke(files, UPDATE_SITE_NAME, false);
            } catch (final NoSuchMethodException e) {
                site = filesClass.getMethod("getUpdateSite", String.class)
                        .invoke(files, UPDATE_SITE_NAME);
            }
            if (site == null) {
                showUnsubscribeFailure("The Neuroanatomy update site was not found in your installation.");
                return;
            }
            // Deactivate: try deactivateUpdateSite(UpdateSite) on FilesCollection first,
            // then setActive(boolean) on UpdateSite, then direct field access
            boolean deactivated = false;
            try {
                final java.lang.reflect.Method deactivate = filesClass.getMethod(
                        "deactivateUpdateSite", site.getClass());
                deactivate.invoke(files, site);
                deactivated = true;
            } catch (final NoSuchMethodException ignored) {
                // do nothing
            }
            if (!deactivated) {
                try {
                    site.getClass().getMethod("setActive", boolean.class).invoke(site, false);
                    deactivated = true;
                } catch (final NoSuchMethodException ignored) {
                    // do nothing
                }
            }
            if (!deactivated) {
                // Last resort: direct field access
                final java.lang.reflect.Field activeField = site.getClass().getDeclaredField("active");
                activeField.setAccessible(true);
                activeField.setBoolean(site, false);
            }
            // Persist the deactivated state
            try {
                filesClass.getMethod("write").invoke(files);
            } catch (final Exception writeEx) {
                // Filesystem may be read-only (e.g., macOS App Translocation!?)
                if (log != null) {
                    log.debug("Could not write db.xml.gz (read-only filesystem?)", writeEx);
                }
                launchUpdaterWithInstructions();
                return;
            }

            if (log != null) {
                log.info("Neuroanatomy update site has been deactivated. Launching updater...");
            }

            // Launch the Updater UI so the user can review and apply the changes
            launchUpdater();

        } catch (final Exception e) {
            if (log != null) {
                log.warn("Failed to programmatically deactivate update site", e);
            }
            showUnsubscribeFailure(
                    "Automatic unsubscription failed. " + e.getClass().getSimpleName()
                            + (e.getMessage() != null ? ": " + e.getMessage() : ""));
        }
    }

    /**
     * Launches the Fiji Updater UI via {@code CommandService}. The updater will
     * see the Neuroanatomy site already deactivated and present the appropriate
     * file changes (downgrades, removals) for the user to review and apply.
     */
    private void launchUpdater() {
        try {
            final String className = "org.scijava.command.CommandService";
            final Object cmdService = getContext().service(className);
            if (cmdService == null) {
                showUpdaterFallback();
                return;
            }
            final Class<?> updaterClass = Class.forName("net.imagej.ui.swing.updater.ImageJUpdater");
            final Class<?> cmdServiceClass = Class.forName(className);
            cmdServiceClass.getMethod("run", Class.class, boolean.class, Object[].class)
                    .invoke(cmdService, updaterClass, true, new Object[0]);
        } catch (final Exception e) {
            if (log != null) {
                log.debug("Could not launch updater programmatically", e);
            }
            showUpdaterFallback();
        }
    }

    private void showUpdaterFallback() {
        JOptionPane.showMessageDialog(null,
                "<html><body style='width:400px;'>"
                        + "<p>The <b>Neuroanatomy</b> update site has been deactivated.</p>"
                        + "<p>Please run the updater (<i>Help &gt; Update...</i>) "
                        + "and click <b>Apply changes</b> to complete the removal.</p>"
                        + "</body></html>",
                "Run Updater to Complete",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Fallback when the db.xml.gz file could not be written.
     * Instructs the user to uncheck Neuroanatomy manually.
     */
    private void launchUpdaterWithInstructions() {
        JOptionPane.showMessageDialog(null,
                "<html><body style='width:400px;'>"
                        + "<p>The Neuroanatomy site could not be deactivated automatically "
                        + "(the Fiji installation appears to be on a read-only filesystem).</p>"
                        + "<p>Once you have move Fiji to a writable directory:</p>"
                        + "<ol>"
                        + "<li>Run the Updater (Help &gt; Update...)</li>"
                        + "<li>Click <b>Manage Update Sites</b></li>"
                        + "<li>Uncheck <b>Neuroanatomy</b></li>"
                        + "<li>Click <b>Apply and Close</b></li>"
                        + "<li>Click <b>Apply Changes</b></li>"
                        + "</ol>"
                        + "</body></html>",
                "Manual Step Required",
                JOptionPane.INFORMATION_MESSAGE);
        launchUpdater();
    }

    private void showUnsubscribeFailure(final String reason) {
        JOptionPane.showMessageDialog(null,
                "<html><body style='width:400px;'>"
                        + "<p>" + reason + "</p>"
                        + "<p>You can unsubscribe manually via "
                        + "<i>Help &gt; Update... &gt; Manage update sites</i>, "
                        + "then uncheck <b>Neuroanatomy</b>.</p>"
                        + "</body></html>",
                "Manual Unsubscription Required",
                JOptionPane.WARNING_MESSAGE);
    }
}
