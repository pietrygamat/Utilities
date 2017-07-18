/*
 * Copyright 2015 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.util.jna.linux;

import org.slf4j.LoggerFactory;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;

import dorkbox.util.OS;
import dorkbox.util.OSUtil;
import dorkbox.util.Swt;
import dorkbox.util.jna.JnaHelper;

/**
 * Bindings for GTK+ 2. Bindings that are exclusively for GTK+ 3 are in that respective class
 * <p>
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
@SuppressWarnings({"Duplicates", "SameParameterValue", "DeprecatedIsStillUsed", "WeakerAccess"})
public
class GtkLoader {
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-x11-2.0.so.0 | grep gtk
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-3.so.0 | grep gtk
    // objdump -T /usr/local/lib/libgtk-3.so.0 | grep gtk

    // For funsies to look at, SyncThing did a LOT of work on compatibility in python (unfortunate for us, but interesting).
    // https://github.com/syncthing/syncthing-gtk/blob/b7a3bc00e3bb6d62365ae62b5395370f3dcc7f55/syncthing_gtk/statusicon.py

    // NOTE: AppIndicator uses this info to figure out WHAT VERSION OF appindicator to use: GTK2 -> appindicator1, GTK3 -> appindicator3
    static boolean isGtk2;
    static boolean isGtk3;
    static boolean isLoaded;


    static boolean alreadyRunningGTK;

    static Function gtk_status_icon_position_menu = null;

    static int MAJOR;
    static int MINOR;
    static int MICRO;

    /*
     * We can have GTK v3 or v2.
     *
     * Observations:
     * JavaFX uses GTK2, and we can't load GTK3 if GTK2 symbols are loaded
     * SWT uses GTK2 or GTK3. We do not work with the GTK3 version of SWT.
     */
    static {
        boolean shouldUseGtk2 = GtkEventDispatch.FORCE_GTK2;
        boolean _isGtk2 = false;
        boolean _isLoaded = false;
        boolean _alreadyRunningGTK = false;
        int major = 0;
        int minor = 0;
        int micro = 0;

        boolean shouldLoadGtk = !(OS.isWindows() || OS.isMacOsX());
        if (!shouldLoadGtk) {
            _isLoaded = true;
        }


        if (OSUtil.Linux.isKali()) {
            // Kali linux has some WEIRD graphical oddities via GTK3. GTK2 looks just fine.
            shouldUseGtk2 = true;
        }

        // we can force the system to use the swing indicator, which WORKS, but doesn't support transparency in the icon. However, there
        // are certain GTK functions we might want to use (even if we are Swing or AWT), so we load GTK anyways...

        // in some cases, we ALWAYS want to try GTK2 first
        String gtk2LibName = "gtk-x11-2.0";
        String gtk3LibName = "libgtk-3.so.0";

        if (!_isLoaded && shouldUseGtk2) {
            try {
                NativeLibrary library = JnaHelper.register(gtk2LibName, Gtk2.class);

                _isGtk2 = true;

                major = library.getGlobalVariableAddress("gtk_major_version").getInt(0);
                minor = library.getGlobalVariableAddress("gtk_minor_version").getInt(0);
                micro = library.getGlobalVariableAddress("gtk_micro_version").getInt(0);

                gtk_status_icon_position_menu = library.getFunction( "gtk_status_icon_position_menu");
                Function gtk_main_level = library.getFunction("gtk_main_level");

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
                _alreadyRunningGTK = gtk_main_level.invokeInt(null) != 0;
                _isLoaded = true;
                if (GtkEventDispatch.DEBUG) {
                    LoggerFactory.getLogger(GtkLoader.class).debug("GTK: {}", gtk2LibName);
                }
            } catch (Throwable e) {
                if (GtkEventDispatch.DEBUG) {
                    LoggerFactory.getLogger(GtkLoader.class).error("Error loading library", e);
                }
            }
        }

        // now for the defaults...

        // start with version 3
        if (!_isLoaded) {
            try {
                // have to get the version information FIRST, because there are some really old GTK3 libraries out there.
                NativeLibrary library = JnaHelper.register(gtk3LibName, Gtk3VersionInfo.class);

                Gtk3VersionInfo version = new Gtk3VersionInfo();
                major = version.gtk_get_major_version();
                minor = version.gtk_get_minor_version();
                micro = version.gtk_get_micro_version();
                library.dispose();

                library = JnaHelper.register(gtk3LibName, Gtk3.class);
                if (major >= 3 && minor >= 10) {
                    // Abusing static fields this way is not proper, but it gets the job done nicely.
                    Gtk3.gdk_window_get_scale_factor = library.getFunction("gdk_window_get_scale_factor");
                }

                gtk_status_icon_position_menu = library.getFunction( "gtk_status_icon_position_menu");
                Function gtk_main_level = library.getFunction("gtk_main_level");

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
                _alreadyRunningGTK = gtk_main_level.invokeInt(null) != 0;
                _isLoaded = true;

                if (GtkEventDispatch.DEBUG) {
                    LoggerFactory.getLogger(GtkLoader.class).debug("GTK: {}", gtk3LibName);
                }
            } catch (Throwable e) {
                if (GtkEventDispatch.DEBUG) {
                    LoggerFactory.getLogger(GtkLoader.class).error("Error loading library.", e);
                }
            }
        }

        // now version 2
        if (!_isLoaded) {
            try {
                NativeLibrary library = JnaHelper.register(gtk2LibName, Gtk2.class);

                _isGtk2 = true;
                major = library.getGlobalVariableAddress("gtk_major_version").getInt(0);
                minor = library.getGlobalVariableAddress("gtk_minor_version").getInt(0);
                micro = library.getGlobalVariableAddress("gtk_micro_version").getInt(0);

                gtk_status_icon_position_menu = Function.getFunction(gtk2LibName, "gtk_status_icon_position_menu");
                Function gtk_main_level = library.getFunction("gtk_main_level");

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
                _alreadyRunningGTK = gtk_main_level.invokeInt(null) != 0;
                _isLoaded = true;
                if (GtkEventDispatch.DEBUG) {
                    LoggerFactory.getLogger(GtkLoader.class).debug("GTK: {}", gtk2LibName);
                }
            } catch (Throwable e) {
                if (GtkEventDispatch.DEBUG) {
                    LoggerFactory.getLogger(GtkLoader.class).error("Error loading library", e);
                }
            }
        }

        if (shouldLoadGtk && _isLoaded) {
            isLoaded = true;

            // depending on how the system is initialized, SWT may, or may not, have the gtk_main loop running. It will EVENTUALLY run, so we
            // do not want to run our own GTK event loop.
            _alreadyRunningGTK |= Swt.isLoaded;

            alreadyRunningGTK = _alreadyRunningGTK;
            isGtk2 = _isGtk2;
            isGtk3 = !_isGtk2;

            MAJOR = major;
            MINOR = minor;
            MICRO = micro;
        }
        else {
            isLoaded = false;

            alreadyRunningGTK = false;
            isGtk2 = false;
            isGtk3 = false;

            MAJOR = 0;
            MINOR = 0;
            MICRO = 0;
        }

        // This is so that queries for the GTK version DO NOT try to load GTK
        OSUtil.DesktopEnv.isGtk2 = isGtk2;
        OSUtil.DesktopEnv.isGtk3 = isGtk3;
        OSUtil.DesktopEnv.isGtkLoaded = isLoaded;

        if (shouldLoadGtk) {
            if (!_isLoaded) {
                throw new RuntimeException("We apologize for this, but we are unable to determine the GTK library is in use, " +
                                           "or even if it is in use... Please create an issue for this and include your OS type and configuration.");
            }
        }
    }
}

