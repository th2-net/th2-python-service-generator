/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

pipeline {
    agent { label "sailfish" }
    options { timestamps () }
    tools {
        jdk 'openjdk-11.0.2'
    }
    environment {
        VERSION_MAINTENANCE = """${sh(
                            returnStdout: true,
                            script: 'git rev-list --count HEAD'
                            ).trim()}"""
        TH2_REGISTRY = credentials('TH2_REGISTRY_USER')
        TH2_REGISTRY_URL = credentials('TH2_REGISTRY')
        GRADLE_SWITCHES = " -Pversion_build=${BUILD_NUMBER} -Pversion_maintenance=${VERSION_MAINTENANCE}"
    }
    stages {
        stage ('Config Build Info') {
            steps {
                rtBuildInfo (
                    captureEnv: true
                )
            }
        }
        stage('Build') {
            steps {
                rtGradleRun (
                    usesPlugin: true, // Artifactory plugin already defined in build script
                    useWrapper: true,
                    rootDir: "./",
                    buildFile: 'build.gradle',
                    tasks: "clean build ${GRADLE_SWITCHES}",
                )
            }
        }
        stage('Docker publish') {
            steps {
                sh """
                    docker login -u ${TH2_REGISTRY_USR} -p ${TH2_REGISTRY_PSW} ${TH2_REGISTRY_URL}
                    ./gradlew dockerPush -Puse_last_sailfish_plugin ${GRADLE_SWITCHES} \
                    -Ptarget_docker_repository=${TH2_REGISTRY_URL}
                """
            }
        }
        stage('Publish report') {
            steps {
                script {
                    def properties = readProperties  file: 'gradle.properties'
                                        def dockerImageVersion = "${properties['version_major'].trim()}.${properties['version_minor'].trim()}.${VERSION_MAINTENANCE}.${BUILD_NUMBER}"

                    def changeLogs = ""
                    try {
                        def changeLogSets = currentBuild.changeSets
                        for (int changeLogIndex = 0; changeLogIndex < changeLogSets.size(); changeLogIndex++) {
                            def entries = changeLogSets[changeLogIndex].items
                            for (int itemIndex = 0; itemIndex < entries.length; itemIndex++) {
                                def entry = entries[itemIndex]
                                changeLogs += "\n${entry.msg}"
                            }
                        }
                    } catch(e) {
                        println "Exception occurred: ${e}"
                    }

                    currentBuild.description = "docker-image-version = ${dockerImageVersion}"
                }
            }
        }
    }
}
