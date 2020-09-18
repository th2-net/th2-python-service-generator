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

package com.exactpro.th2.proto.service.generator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.proto.service.generator.core.antlr.Protobuf3Lexer;
import com.exactpro.th2.proto.service.generator.core.antlr.Protobuf3Parser;
import com.exactpro.th2.proto.service.generator.core.antlr.Protobuf3Parser.ProtoContext;
import com.exactpro.th2.proto.service.generator.service.MethodDescription;
import com.exactpro.th2.proto.service.generator.service.ServiceDescription;
import com.exactpro.th2.proto.service.generator.service.ServiceWriter;

public class ServiceGenerator {

    private static final int START_RPC_DECLARATION_INDEX = 3;
    private static final int RPC_METHOD_NAME_INDEX = 1;
    private static final int RPC_METHOD_REQUEST_TYPE_INDEX = 3;
    private static final int RPC_METHOD_RESPONSE_TYPE_INDEX = 7;

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final File protoFileOrFolder;
    private final boolean recursive;

    public ServiceGenerator(File protoFileOrFolder, boolean recursive) throws FileNotFoundException {
        this.protoFileOrFolder = protoFileOrFolder;

        if (!protoFileOrFolder.exists()) {
            throw new FileNotFoundException("Can not find file or folder by path: " + protoFileOrFolder.toPath());
        }

        this.recursive = recursive;
    }

    public ServiceGenerator(File protoFileOrFolder) throws FileNotFoundException {
        this(protoFileOrFolder, true);
    }

    public void generate(Path outputFolder, ServiceWriter writer) {
        var files = loadInputFiles();

        if (files.isEmpty()) {
            logger.warn("Can not find proto files");
        }

        Path protoFileOrFolderPath = protoFileOrFolder.toPath();

        for (File file : files) {
            Path filePath = file.toPath();

            try {
                ProtoContext protoTree = parseProtoFromInputFile(file);

                ServiceDescription[] serviceDescriptions = getServicesFromParsedFile(protoTree);

                if (serviceDescriptions.length < 1) {
                    logger.warn("Can not find services in file by path: {}. File will not created", filePath);
                    continue;
                }

                Path pathToFile = protoFileOrFolderPath.relativize(filePath);
                File outputFile;

                String protoFileName = filePath.getFileName().toString();
                int extensionIndex = protoFileName.lastIndexOf(".");
                if (extensionIndex > -1) {
                    protoFileName = protoFileName.substring(0, extensionIndex);
                }

                String outputFileName = writer.getFileName(protoFileName);

                Path parent = pathToFile.getParent();
                outputFile = (parent == null ? outputFolder.resolve(outputFileName) : outputFolder.resolve(parent).resolve(outputFileName)).toFile();

                if (!outputFile.exists()
                        && (outputFile.toPath().getParent() != null
                            && !outputFile.toPath().getParent().toFile().exists()
                            && !outputFile.toPath().getParent().toFile().mkdirs()
                            || !outputFile.createNewFile())) {
                    logger.error("Can not create output file for file '{}'. Output path: '{}'", filePath, outputFile.toPath());
                    continue;
                }

                try (FileOutputStream output = new FileOutputStream(outputFile)) {
                    for (ServiceDescription serviceDescription : serviceDescriptions) {
                        writer.write(serviceDescription, protoFileName, outputFileName, output);
                    }
                    logger.info("Generate service file by path = " + outputFile.getAbsolutePath());
                } catch (Exception e) {
                    logger.error("Can not write service description to file by path '{}'", outputFile.toPath(), e);
                }

            } catch (IOException e) {
                logger.error("Can not parsed proto file by path: {}", filePath, e);
            }
        }
    }


    private List<File> loadInputFiles() {
        if (protoFileOrFolder.isFile()) {
            logger.debug("Loaded single file. Path = {}", protoFileOrFolder.getAbsolutePath());
            return Collections.singletonList(protoFileOrFolder);
        } else if (protoFileOrFolder.isDirectory()) {
            logger.debug("Loaded directory. Path = {}", protoFileOrFolder.getAbsolutePath());
            return loadFilesRecursive(protoFileOrFolder, recursive);
        } else {
            throw new RuntimeException("Can not get access to file or folder by path: " + protoFileOrFolder.toPath());
        }
    }

    private List<File> loadFilesRecursive(File folder, boolean recursive) {
        List<File> list = new ArrayList<>();
        for (File file : folder.listFiles()) {
            if (file.isFile()) {
                list.add(file);
                logger.info("Find proto file by path = " + file.getAbsolutePath());
            }

            if (file.isDirectory() && recursive) {
                list.addAll(loadFilesRecursive(file, recursive));
            }
        }

        return list;
    }

    private ServiceDescription[] getServicesFromParsedFile(ProtoContext protoTree) {
        List<ServiceDescription> descriptions = new ArrayList<>();
        for (ParseTree child : protoTree.children) {
            if (child.getChildCount() > 0) {

                var potentialEntity = child.getChild(0);

                if (potentialEntity.getChildCount() > 0) {

                    var option = potentialEntity.getChild(0).getText();

                    if (option.equals("service")) {
                        var entityName = potentialEntity.getChild(1).getText();
                        ServiceDescription serviceDescription = new ServiceDescription(entityName);
                        serviceDescription.getMethods().addAll(getMethodsDescriptions(potentialEntity));
                        descriptions.add(serviceDescription);
                    }
                }
            }
        }

        return descriptions.toArray(new ServiceDescription[0]);
    }

    private List<MethodDescription> getMethodsDescriptions(ParseTree serviceNode) {
        List<MethodDescription> methodDescriptors = new ArrayList<>();

        for (int i = START_RPC_DECLARATION_INDEX; i < serviceNode.getChildCount(); i++) {
            var methodNode = serviceNode.getChild(i);

            if (methodNode.getChildCount() == 0) {
                continue;
            }

            var methodName = methodNode.getChild(RPC_METHOD_NAME_INDEX).getText();
            var methodRequestType = methodNode.getChild(RPC_METHOD_REQUEST_TYPE_INDEX).getText();
            var methodResponseType = methodNode.getChild(RPC_METHOD_RESPONSE_TYPE_INDEX).getText();

            methodDescriptors.add(new MethodDescription(methodName, methodResponseType, methodRequestType));
        }

        return methodDescriptors;
    }

    private ProtoContext parseProtoFromInputFile(File inputFile) throws IOException {
        Protobuf3Lexer lexer = new Protobuf3Lexer(CharStreams.fromFileName((inputFile.toString())));
        Protobuf3Parser parser = new Protobuf3Parser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        return parser.proto();
    }

}
