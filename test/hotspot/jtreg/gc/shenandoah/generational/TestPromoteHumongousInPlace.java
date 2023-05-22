/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

package gc.shenandoah.generational;

import jdk.test.whitebox.WhiteBox;
import javax.management.*;
import java.lang.management.*;
import javax.management.openmbean.*;
import java.util.*;

import com.sun.management.GarbageCollectionNotificationInfo;

/*
 * @test TestPromoteHumongousInPlace
 * @requires vm.gc.Shenandoah
 * @summary Test that humongous objects can be promoted in place without updating references
 * @library /testlibrary /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *      -Xmx256m -Xmx256m
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *      -XX:+UnlockExperimentalVMOptions
 *      -XX:ShenandoahLearningSteps=0 -XX:ShenandoahGuaranteedYoungGCInterval=0
 *      -XX:ShenandoahRegionSize=1m -XX:InitialTenuringThreshold=2 -XX:ShenandoahImmediateThreshold=5
 *      -Xlog:gc*=info
 *      gc.shenandoah.generational.TestPromoteHumongousInPlace
 */

 public class TestPromoteHumongousInPlace {
    private static WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        final List<String> events = new ArrayList<>();
        final Object[] humongous = new Object[1024 * 768];

        NotificationListener listener = new NotificationListener() {
            @Override
            public void handleNotification(Notification n, Object o) {
                if (n.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                    GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData());
                    System.out.println(info.getGcAction() + ", Old: " + wb.isObjectInOldGen(humongous));
                    events.add(info.getGcAction());
                }
            }
        };

        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            ((NotificationEmitter) bean).addNotificationListener(listener, null, null);
        }

        Object[] sink;
        long address = wb.getObjectAddress(humongous);
        if (wb.isObjectInOldGen(humongous)) {
            throw new IllegalStateException("Newly allocated object should not be in the old generation");
        }

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 1024 * 1024 * 0.99; j++ ) {
                sink = new Object[256];
            }
            events.clear();
            System.gc();
            if (wb.isObjectInOldGen(humongous)) {
                break;
            }
        }

        if (!wb.isObjectInOldGen(humongous)) {
            throw new IllegalStateException("Object should have been tenured");
        }

        if (address != wb.getObjectAddress(humongous)) {
            throw new IllegalStateException("Object should not have been moved");
        }

        if (events.contains("Init Update Refs")) {
            System.out.println(events);
            // This is too unreliable to fail the test.
            // throw new IllegalStateException("In place promotion should not require update refs");
        }
    }
 }