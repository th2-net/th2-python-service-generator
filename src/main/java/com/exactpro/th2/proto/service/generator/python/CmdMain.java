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

package com.exactpro.th2.proto.service.generator.python;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;

import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.proto.service.generator.core.antlr.Protobuf3Lexer;
import com.exactpro.th2.proto.service.generator.core.antlr.Protobuf3Parser;
import com.exactpro.th2.proto.service.generator.core.antlr.Protobuf3Parser.ProtoContext;
import com.exactpro.th2.proto.service.generator.python.service.MethodDescription;
import com.exactpro.th2.proto.service.generator.python.service.ServiceDescription;
import com.exactpro.th2.proto.service.generator.python.service.ServiceWriter;

public class CmdMain {

    private static Logger logger = LoggerFactory.getLogger("PythonServiceGenerator");

    public static void main(String[] args) {

        Options options = new Options();

        options.addOption(Option.builder("p").longOpt("proto").hasArg().desc("Proto file or folder with proto files").required().build());
        options.addOption(Option.builder("r").longOpt("recursive").hasArg(false).desc("Recursive file search in proto folder").build());
        options.addOption(Option.builder("o").longOpt("out").hasArg().desc("Output folder").required().build());
        options.addOption(Option.builder("w").longOpt("writer").hasArg().desc("Short class name of service writer").build());

        try {
            CommandLine cmd = new DefaultParser().parse(options, args);

            String outputPathString = cmd.getOptionValue("out");
            Path outputPath = Path.of(outputPathString);
            File outputFolder = Path.of(outputPathString).toFile();
            if (!outputFolder.exists() && !outputFolder.mkdirs()) {
                logger.error("Can not create output folder by path: " + outputPathString);
                return;
            }

            if (outputFolder.isFile()) {
                logger.error("Output path is file. Output path: " + outputPathString);
                return;
            }

            Path protoFileOrFolder = Path.of(cmd.getOptionValue("proto"));
            File[] files = loadFiles(protoFileOrFolder, cmd.hasOption("recursive"));

            String writerClassName = cmd.getOptionValue("writer");
            ServiceWriter writer = loadServiceWriter(writerClassName);
            if (writer == null) {
                logger.error("Can not find service writer." + (writerClassName != null ? "Writer class: " + writerClassName :""));
                return;
            }

            for (File protoFile : files) {
                try {
                    Protobuf3Lexer lexer = new Protobuf3Lexer(new ANTLRFileStream(protoFile.toPath().toString()));
                    Protobuf3Parser parser = new Protobuf3Parser(new CommonTokenStream(lexer));
                    parser.removeErrorListeners();
                    ProtoContext protoTree = parser.proto();

                    ServiceDescription[] serviceDescriptions = getServices(protoTree);

                    if (serviceDescriptions.length < 1) {
                        logger.warn("Can not find services in file by path: {}", protoFile.toPath());
                    }

                    Path pathToFile = protoFileOrFolder.relativize(protoFile.toPath());
                    File outputFile;

                    String protoFileName = protoFile.toPath().getFileName().toString();
                    int extensionIndex = protoFileName.lastIndexOf(".");
                    if (extensionIndex > -1) {
                        protoFileName = protoFileName.substring(0, extensionIndex);
                    }

                    String outputFileName = writer.getFileName(protoFileName);

                    Path parent = pathToFile.getParent();
                    outputFile = (parent == null ? outputPath.resolve(outputFileName) : outputPath.resolve(parent).resolve(outputFileName)).toFile();

                    if (!outputFile.exists()
                    && (outputFile.toPath().getParent() != null && !outputFile.toPath().getParent().toFile().exists() && !outputFile.toPath().getParent().toFile().mkdirs()
                    || !outputFile.createNewFile())) {
                        logger.error("Can not create output file for file '{}'. Output path: '{}'", protoFile.toPath(), outputFile.toPath());
                        continue;
                    }

                    try (FileOutputStream output = new FileOutputStream(outputFile)) {
                        for (ServiceDescription serviceDescription : serviceDescriptions) {
                            writer.write(serviceDescription, protoFileName, outputFileName, output);
                        }
                    } catch (Exception e) {
                        logger.error("Can not write service description to file by path '{}'", outputFile.toPath(), e);
                    }

                } catch (IOException e) {
                    logger.error("Can not parsed proto file by path:" + protoFile.toPath().toString(), e);
                }
            }
        } catch (ParseException e) {
            logger.error("Can not parse arguments", e);
            printHelp(options);
        } catch (FileNotFoundException e) {
            logger.error("Can not find file or folder", e);
            printHelp(options);
        }

    }

    private static File[] loadFiles(Path fileOrFolder, boolean recursive) throws FileNotFoundException {
        File protoFileOrFolder = fileOrFolder.toFile();
        if (!protoFileOrFolder.exists()) {
            throw new FileNotFoundException("Can not find file or folder by path: " + fileOrFolder);
        }

        if (protoFileOrFolder.isFile()) {
            return new File[]{protoFileOrFolder};
        } else if (protoFileOrFolder.isDirectory()) {
            return loadFiles(protoFileOrFolder, recursive).toArray(new File[0]);
        } else {
            throw new RuntimeException("Can not get access to file or folder by path: " + fileOrFolder);
        }
    }

    private static List<File> loadFiles(File folder, boolean recursive) {
        List<File> list = new ArrayList<>();
        for (File file : folder.listFiles()) {
            if (file.isFile()) {
                list.add(file);
            }

            if (file.isDirectory() && recursive) {
                list.addAll(loadFiles(file, recursive));
            }
        }

        return list;
    }

    private static ServiceWriter loadServiceWriter(String writerClassName) {
        var loadedClasses = ServiceLoader.load(ServiceWriter.class).stream();
        if (writerClassName == null || writerClassName.isEmpty()) {
            return loadedClasses.findFirst().map(Provider::get).orElse(null);
        } else {
            return loadedClasses
                    .filter(provider -> provider.type().getSimpleName().equals(writerClassName))
                    .findFirst()
                    .map(Provider::get)
                    .orElse(null);
        }
    }

    private static ServiceDescription[] getServices(ProtoContext protoTree) {
        List<ServiceDescription> descriptions = new ArrayList<>();
        for (ParseTree child : protoTree.children) {
            if (child.getChildCount() > 0) {

                var potentialEntity = child.getChild(0);

                if (potentialEntity.getChildCount() > 0) {

                    var option = potentialEntity.getChild(0).getText();

                    if (option.equals("service")) {
                        var entityName = potentialEntity.getChild(1).getText();
                        ServiceDescription serviceDescription = new ServiceDescription(entityName);
                        serviceDescription.getMethods().addAll(getMethodDescriptors(potentialEntity));
                        descriptions.add(serviceDescription);
                    }
                }
            }
        }

        return descriptions.toArray(new ServiceDescription[0]);
    }

    private static List<MethodDescription> getMethodDescriptors(ParseTree serviceNode) {

        var startRpcDeclarationIndex = 3;

        var methodNameIndex = 1;
        var methodRequestTypeIndex = 3;
        var methodResponseTypeIndex = 7;

        List<String> comments = new ArrayList<>();
        List<MethodDescription> methodDescriptors = new ArrayList<>();

        for (int i = startRpcDeclarationIndex; i < serviceNode.getChildCount(); i++) {
            var methodNode = serviceNode.getChild(i);

            if (isChildless(methodNode)) {
                if (isComment(methodNode)) {
                    comments.add(extractCommentText(methodNode));
                }
                continue;
            }


            var methodName = methodNode.getChild(methodNameIndex).getText();
            var methodRequestType = methodNode.getChild(methodRequestTypeIndex).getText();
            var methodResponseType = methodNode.getChild(methodResponseTypeIndex).getText();

            methodDescriptors.add(new MethodDescription(methodName, methodResponseType, methodRequestType));

            comments.clear();
        }

        return methodDescriptors;
    }

    private static boolean isChildless(ParseTree node) {
        return node.getChildCount() == 0;
    }

    private static boolean isComment(ParseTree node) {
        var stringNode = node.toString().strip();
        return stringNode.startsWith("/**") && stringNode.endsWith("*/")
                || stringNode.startsWith("/*") && stringNode.endsWith("*/")
                || stringNode.startsWith("//");
    }

    private static String extractCommentText(ParseTree commentNode) {
        return commentNode.toString().replace("/**", "")
                .replace("/*", "")
                .replace("*/", "")
                .replace("//", "")
                .strip();
    }

    private static void printHelp(Options options) {
        new HelpFormatter().printHelp("python-service-generator", options);
    }

}
