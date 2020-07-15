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

package com.exactpro.th2.proto.service.generator.python.python;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import com.exactpro.th2.proto.service.generator.python.service.MethodDescription;
import com.exactpro.th2.proto.service.generator.python.service.ServiceDescription;
import com.exactpro.th2.proto.service.generator.python.service.ServiceWriter;

public class PythonServiceWriter implements ServiceWriter {

    private static final String TAB = "    ";
    @Override
    public void write(ServiceDescription description, String protoFile, String outFile, OutputStream stream) {
        protoFile = protoFile.replace('-', '_');
        try (PrintWriter writer = new PrintWriter(stream, false, StandardCharsets.UTF_8)) {
            writer.println("from . import " + protoFile + "_pb2_grpc as importStub");
            writer.println();
            writer.println("class " + description.getName() + "Service(object):");
            writer.println();
            writer.println(TAB + "def __init__(self, router):");
            writer.println(TAB + TAB + "self.connector = router.__get__connection(" + description.getName() + "Service, importStub." + description.getName() + "Stub)");
            for (MethodDescription method : description.getMethods()) {
                writer.println();
                writer.println(TAB + "def " + method.getName() + "(self, request):");
                writer.println(TAB + TAB + "self.connector.createRequest(\"" + method.getName() + "\",request)");
            }
        }
    }

    @Override
    public String getFileName(String protoFileName) {
        return protoFileName.replace('-', '_') + "_service.py";
    }
}
