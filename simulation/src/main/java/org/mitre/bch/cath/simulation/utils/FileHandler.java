package org.mitre.bch.cath.simulation.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.commons.io.IOUtils;
/** Handler for reading from resources
 * @author H. Haven Liu, The MITRE Corporation
 */

public class FileHandler {
    /** Map resource filename to its location
     *
     * @param filename filename
     * @return file location
     * @throws IOException file not found
     */
    public static String fileToString(String filename) throws IOException {
        return IOUtils.toString(
                Objects.requireNonNull(FileHandler.class.getClassLoader().getResourceAsStream(filename)),
                StandardCharsets.UTF_8);
    }
}

