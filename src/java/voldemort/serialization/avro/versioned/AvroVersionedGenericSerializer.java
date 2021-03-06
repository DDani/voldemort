/*
 * Copyright 2011 LinkedIn, Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package voldemort.serialization.avro.versioned;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;

import voldemort.serialization.SerializationException;
import voldemort.serialization.SerializationUtils;
import voldemort.serialization.Serializer;

/**
 * Avro serializer that uses the generic representation for Avro data. This
 * representation is best for applications which deal with dynamic data, whose
 * schemas are not known until runtime.
 * 
 */
public class AvroVersionedGenericSerializer implements Serializer<Object> {

    private final SortedMap<Integer, String> typeDefVersions;
    private final Integer newestVersion;

    // reader's schema
    private final Schema typeDef;

    /**
     * Constructor accepting the schema definition as a JSON string.
     * 
     * @param schema a serialized JSON object representing a Avro schema.
     */
    public AvroVersionedGenericSerializer(String schema) {

        this.typeDefVersions = new TreeMap<Integer, String>();
        this.typeDefVersions.put(0, schema);
        newestVersion = typeDefVersions.lastKey();
        typeDef = Schema.parse(typeDefVersions.get(newestVersion));
    }

    public AvroVersionedGenericSerializer(Map<Integer, String> typeDefVersions) {

        this.typeDefVersions = new TreeMap<Integer, String>(typeDefVersions);
        newestVersion = this.typeDefVersions.lastKey();
        typeDef = Schema.parse(typeDefVersions.get(newestVersion));

    }

    public byte[] toBytes(Object object) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Encoder encoder = new BinaryEncoder(output);
        GenericDatumWriter<Object> datumWriter = null;

        output.write(newestVersion.byteValue());
        try {
            datumWriter = new GenericDatumWriter<Object>(typeDef);
            datumWriter.write(object, encoder);
            encoder.flush();
        } catch(IOException e) {
            throw new SerializationException(e);
        } finally {
            SerializationUtils.close(output);
        }

        return output.toByteArray();
    }

    public Object toObject(byte[] bytes) {

        Integer version = Integer.valueOf(bytes[0]);

        Schema typeDefWriter = Schema.parse(typeDefVersions.get(version));

        byte[] dataBytes = new byte[bytes.length - 1];
        System.arraycopy(bytes, 1, dataBytes, 0, bytes.length - 1);
        Decoder decoder = DecoderFactory.defaultFactory().createBinaryDecoder(dataBytes, null);
        GenericDatumReader<Object> reader = null;
        try {
            reader = new GenericDatumReader<Object>(typeDefWriter, typeDef);
            // writer's schema
            reader.setSchema(typeDefWriter);
            // Reader's schema
            reader.setExpected(typeDef);
            return reader.read(null, decoder);
        } catch(IOException e) {
            throw new SerializationException(e);
        }

    }
}
