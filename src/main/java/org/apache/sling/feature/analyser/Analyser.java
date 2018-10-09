/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.feature.analyser;

import static org.apache.sling.feature.analyser.task.AnalyzerTaskProvider.getTasks;
import static org.apache.sling.feature.analyser.task.AnalyzerTaskProvider.getTasksByClassName;
import static org.apache.sling.feature.analyser.task.AnalyzerTaskProvider.getTasksByIds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.analyser.task.AnalyserTask;
import org.apache.sling.feature.analyser.task.AnalyserTaskContext;
import org.apache.sling.feature.scanner.BundleDescriptor;
import org.apache.sling.feature.scanner.FeatureDescriptor;
import org.apache.sling.feature.scanner.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Analyser {

    private final AnalyserTask[] tasks;

    private final Scanner scanner;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public Analyser(final Scanner scanner,
            final AnalyserTask...tasks) throws IOException {
        this.tasks = tasks;
        this.scanner = scanner;
    }

    public Analyser(final Scanner scanner,
            final String... taskClassNames)
    throws IOException {
        this(scanner, getTasksByClassName(taskClassNames));
        if ( this.tasks.length != taskClassNames.length ) {
            throw new IOException("Couldn't find all tasks " + Arrays.toString(taskClassNames));
        }
    }

    public Analyser(final Scanner scanner,
                    final Set<String> includes,
                    final Set<String> excludes) throws IOException {
        this(scanner, getTasksByIds(includes, excludes));
    }

    public Analyser(final Scanner scanner) throws IOException {
        this(scanner, getTasks());
    }

    public void analyse(final Feature feature)
    throws Exception {
        this.analyse(feature, null);
    }

    public void analyse(final Feature feature, final ArtifactId fwk)
    throws Exception {
        logger.info("Starting analyzing feature '{}'...", feature.getId());

        final FeatureDescriptor featureDesc = scanner.scan(feature);
        BundleDescriptor bd = null;
        if ( fwk != null ) {
            bd = scanner.scan(fwk, feature.getFrameworkProperties());
        }
        final BundleDescriptor fwkDesc = bd;

        final List<String> warnings = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        // execute analyser tasks
        for(final AnalyserTask task : tasks) {
            logger.info("- Executing {} [{}]...", task.getName(), task.getId());

            task.execute(new AnalyserTaskContext() {

                @Override
                public Feature getFeature() {
                    return feature;
                }

                @Override
                public FeatureDescriptor getFeatureDescriptor() {
                    return featureDesc;
                }

                @Override
                public BundleDescriptor getFrameworkDescriptor() {
                    return fwkDesc;
                }

                @Override
                public void reportWarning(final String message) {
                    warnings.add(message);
                }

                @Override
                public void reportError(final String message) {
                    errors.add(message);
                }
            });
        }

        for(final String msg : warnings) {
            logger.warn(msg);
        }
        for(final String msg : errors) {
            logger.error(msg);
        }

        if ( !errors.isEmpty() ) {
            throw new Exception("Analyser detected errors on Feature '"
                                + feature.getId()
                                + "'. See log output for error messages.");
        }

        logger.info("Feature '"
                    + feature.getId()
                    + "' provisioning model analyzer finished");
    }
}
