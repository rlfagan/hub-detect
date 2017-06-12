/*
 * Copyright (C) 2017 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.integration.hub.detect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.blackducksoftware.integration.hub.bdio.simple.BdioWriter;
import com.blackducksoftware.integration.hub.bdio.simple.DependencyNodeTransformer;
import com.blackducksoftware.integration.hub.bdio.simple.model.DependencyNode;
import com.blackducksoftware.integration.hub.bdio.simple.model.SimpleBdioDocument;
import com.blackducksoftware.integration.hub.detect.bomtool.BomTool;
import com.blackducksoftware.integration.hub.detect.type.BomToolType;
import com.blackducksoftware.integration.util.ExcludedIncludedFilter;
import com.blackducksoftware.integration.util.IntegrationEscapeUtil;
import com.google.gson.Gson;

@Component
public class BomToolManager {
    private final Logger logger = LoggerFactory.getLogger(BomToolManager.class);

    @Autowired
    DetectProperties detectProperties;

    @Autowired
    DetectConfiguration detectConfiguration;

    @Autowired
    private List<BomTool> bomTools;

    @Autowired
    private Gson gson;

    @Autowired
    private DependencyNodeTransformer dependencyNodeTransformer;

    public List<File> createBdioFiles() throws IOException {
        final List<File> createdBdioFiles = new ArrayList<>();
        boolean foundSomeBomTools = false;
        final ExcludedIncludedFilter toolFilter = new ExcludedIncludedFilter("", detectProperties.getBomToolTypeOverride());
        for (final BomTool bomTool : bomTools) {
            try {
                final BomToolType bomToolType = bomTool.getBomToolType();
                final String bomToolTypeString = bomToolType.toString();
                if (!toolFilter.shouldInclude(bomToolTypeString)) {
                    logger.info(String.format("Skipping %s.", bomToolTypeString));
                    continue;
                }
                if (bomTool.isBomToolApplicable() && detectConfiguration.shouldRun(bomTool)) {
                    logger.info(bomToolType + " applies given the current configuration.");
                    final List<DependencyNode> projectNodes = bomTool.extractDependencyNodes();
                    if (projectNodes != null && projectNodes.size() > 0) {
                        foundSomeBomTools = true;
                        createOutput(createdBdioFiles, bomToolType, bomToolTypeString, projectNodes);
                    }
                }
            } catch (final Exception e) {
                // any bom tool failure should not prevent other bom tools from running
                logger.error(bomTool.getBomToolType().toString() + " threw an Exception: " + e.getMessage());
            }
        }

        if (!foundSomeBomTools) {
            logger.info("Could not find any tools to run.");
        }
        return createdBdioFiles;
    }

    private void createOutput(final List<File> createdBdioFiles, final BomToolType bomToolType, final String bomToolTypeString,
            final List<DependencyNode> projectNodes) {
        final File outputDirectory = new File(detectProperties.getOutputDirectoryPath());

        logger.info("Creating " + projectNodes.size() + " project nodes");
        for (final DependencyNode project : projectNodes) {
            final IntegrationEscapeUtil escapeUtil = new IntegrationEscapeUtil();
            final String safeProjectName = escapeUtil.escapeForUri(project.name);
            final String safeVersionName = escapeUtil.escapeForUri(project.version);
            final String safeName = String.format("%s_%s_%s_bdio", bomToolTypeString, safeProjectName, safeVersionName);
            final String filename = String.format("%s.jsonld", safeName);
            final File outputFile = new File(outputDirectory, filename);
            if (outputFile.exists()) {
                outputFile.delete();
            }
            try (final BdioWriter bdioWriter = new BdioWriter(gson, new FileOutputStream(outputFile))) {
                if (StringUtils.isNotBlank(detectProperties.getProjectName())) {
                    project.name = detectProperties.getProjectName();
                }
                if (StringUtils.isNotBlank(detectProperties.getProjectVersionName())) {
                    project.version = detectProperties.getProjectVersionName();
                }
                final SimpleBdioDocument bdioDocument = dependencyNodeTransformer.transformDependencyNode(project);
                if (StringUtils.isNotBlank(detectProperties.getProjectName()) && StringUtils.isNotBlank(detectProperties.getProjectVersionName())) {
                    bdioDocument.billOfMaterials.spdxName = String.format("%s/%s/%s Black Duck I/O Export", project.name, project.version, bomToolTypeString);
                }
                bdioWriter.writeSimpleBdioDocument(bdioDocument);
                createdBdioFiles.add(outputFile);
                logger.info("BDIO Generated: " + outputFile.getAbsolutePath());
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
