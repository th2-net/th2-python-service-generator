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
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.proto.service.generator.service.ServiceWriter;

public class CmdMain {

    private static Logger logger = LoggerFactory.getLogger("PythonServiceGenerator");

    public static void main(String[] args) {

        Options options = new Options();

        options.addOption(Option.builder("p").longOpt("proto").hasArg().desc("Proto file or folder with proto files").required().build());
        options.addOption(Option.builder("r").longOpt("recursive").hasArg(false).desc("Recursive file search in proto folder").build());
        options.addOption(Option.builder("o").longOpt("out").hasArg().desc("Output folder").required().build());
        options.addOption(Option.builder("w").longOpt("writer").hasArg().desc("Short class name of service writer").build());
        options.addOption(Option.builder("h").longOpt("help").hasArg(false).desc("Print help message").build());

        try {
            CommandLine cmd = new DefaultParser().parse(options, args);

            if (cmd.hasOption("h")) {
                printHelp(options);
                return;
            }

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

            String writerClassName = cmd.getOptionValue("writer");
            ServiceWriter writer = loadServiceWriter(writerClassName);

            if (writer == null) {
                logger.error("Can not find service writer." + (writerClassName != null ? "Writer class: " + writerClassName :""));
                return;
            }

            var loader = new ServiceGenerator(protoFileOrFolder.toFile(), cmd.hasOption("recursive"));
            loader.generate(outputPath, writer);

        } catch (ParseException e) {
            logger.error("Can not parse arguments", e);
            printHelp(options);
        } catch (FileNotFoundException e) {
            logger.error("Can not find file or folder", e);
            printHelp(options);
        }

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


    private static void printHelp(Options options) {
        new HelpFormatter().printHelp("python-service-generator", options);
    }

}
