package com.blackducksoftware.integration.hub.detect.bomtool

import java.nio.charset.StandardCharsets

import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import com.blackducksoftware.integration.hub.bdio.simple.model.DependencyNode
import com.blackducksoftware.integration.hub.detect.bomtool.docker.DockerProperties
import com.blackducksoftware.integration.hub.detect.type.BomToolType
import com.blackducksoftware.integration.hub.detect.type.ExecutableType
import com.blackducksoftware.integration.hub.detect.util.executable.Executable

@Component
class DockerBomTool extends BomTool {
    private final Logger logger = LoggerFactory.getLogger(DockerBomTool.class)

    @Autowired
    DockerProperties dockerProperties

    private String dockerExecutablePath
    private String bashExecutablePath

    @Override
    public BomToolType getBomToolType() {
        BomToolType.DOCKER
    }

    @Override
    public boolean isBomToolApplicable() {
        dockerExecutablePath = findDockerExecutable()
        if (!dockerExecutablePath) {
            logger.debug('Could not find docker on the environment PATH')
        }
        bashExecutablePath = findBashExecutable()
        if (!bashExecutablePath) {
            logger.debug('Could not find bash on the environment PATH')
        }
        boolean propertiesOk = detectProperties.dockerInspectorVersion && (detectProperties.dockerTar || detectProperties.dockerImage)
        if (!propertiesOk) {
            logger.debug('The docker properties are not sufficient to run')
        }

        dockerExecutablePath && propertiesOk
    }

    @Override
    public List<DependencyNode> extractDependencyNodes() {
        URL hubDockerInspectorShellScriptUrl = new URL("https://blackducksoftware.github.io/hub-docker-inspector/hub-docker-inspector-${detectProperties.dockerInspectorVersion}.sh")
        File dockerInstallDirectory = new File(detectProperties.dockerInstallPath)
        String shellScriptContents = hubDockerInspectorShellScriptUrl.openStream().getText(StandardCharsets.UTF_8.name())
        File shellScriptFile = new File(dockerInstallDirectory, "hub-docker-inspector-${detectProperties.dockerInspectorVersion}.sh")
        shellScriptFile.delete()
        shellScriptFile << shellScriptContents
        shellScriptFile.setExecutable(true)

        String path = System.getenv('PATH')
        File dockerExecutableFile = new File(dockerExecutablePath)
        path += File.pathSeparator + dockerExecutableFile.parentFile.absolutePath
        Map<String, String> environmentVariables = [PATH: path]

        List<String> dockerShellScriptArguments = dockerProperties.createDockerArgumentList()
        String bashScriptArg = StringUtils.join(dockerShellScriptArguments, ' ')

        List<String> bashArguments = [
            "-c",
            "${shellScriptFile.absolutePath} ${bashScriptArg}"
        ]

        Executable dockerExecutable = new Executable(dockerInstallDirectory, environmentVariables, bashExecutablePath, bashArguments)
        executableRunner.executeLoudly(dockerExecutable)
        return [];
    }

    private String findDockerExecutable() {
        String dockerPath = detectProperties.dockerPath
        if (!dockerPath?.trim()) {
            dockerPath = executableManager.getPathOfExecutable(ExecutableType.DOCKER)?.trim()
        }
        dockerPath
    }

    private String findBashExecutable() {
        String bashPath = detectProperties.bashPath
        if (!bashPath?.trim()) {
            bashPath = executableManager.getPathOfExecutable(ExecutableType.BASH)?.trim()
        }
        bashPath
    }
}
