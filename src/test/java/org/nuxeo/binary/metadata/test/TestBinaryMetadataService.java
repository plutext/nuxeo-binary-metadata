/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *      Vladimir Pasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.binary.metadata.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.WriterAppender;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.binary.metadata.api.service.BinaryMetadataService;
import org.nuxeo.binary.metadata.api.service.BinaryMetadataServiceImpl;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

import com.google.inject.Inject;

/**
 * @since 7.1
 */
@RunWith(FeaturesRunner.class)
@Features(BinaryMetadataFeature.class)
@LocalDeploy({ "org.nuxeo.binary.metadata.test:OSGI-INF/binary-metadata-contrib-test.xml" })
@RepositoryConfig(cleanup = Granularity.METHOD, init = BinaryMetadataServerInit.class)
public class TestBinaryMetadataService {

    @Inject
    BinaryMetadataService binaryMetadataService;

    @Inject
    CoreSession session;

    List<String> musicMetadata = new ArrayList<String>() {
        {
            add("ID3:Title");
            add("ID3:Lyrics-por");
            add("ID3:Publisher");
            add("ID3:Comment");
        }
    };

    List<String> PSDMetadata = new ArrayList<String>() {
        {
            add("EXIF:ImageHeight");
            add("EXIF:Software");
        }
    };

    private static final Map<String, Object> inputPSDMetadata;

    static {
        inputPSDMetadata = new HashMap<>();
        inputPSDMetadata.put("EXIF:ImageHeight", 200);
        inputPSDMetadata.put("EXIF:Software", "Nuxeo");
    }

    @Test
    public void itShouldExtractAllMetadataFromBinary() {
        // Get the document with MP3 attached
        DocumentModel musicFile = BinaryMetadataServerInit.getFile(0, session);
        BlobHolder musicBlobHolder = musicFile.getAdapter(BlobHolder.class);
        Map<String, Object> blobProperties = binaryMetadataService.readMetadata(musicBlobHolder.getBlob());
        assertNotNull(blobProperties);
        assertEquals(48, blobProperties.size());
        assertEquals("Twist", blobProperties.get("ID3:Title").toString());
        assertEquals("Divine Recordings", blobProperties.get("ID3:Publisher").toString());
    }

    @Test
    public void itShouldExtractGivenMetadataFromBinary() {
        // Get the document with MP3 attached
        DocumentModel musicFile = BinaryMetadataServerInit.getFile(0, session);
        BlobHolder musicBlobHolder = musicFile.getAdapter(BlobHolder.class);
        Map<String, Object> blobProperties = binaryMetadataService.readMetadata(musicBlobHolder.getBlob(),
                musicMetadata);
        assertNotNull(blobProperties);
        assertEquals(4, blobProperties.size());
        assertEquals("Twist", blobProperties.get("ID3:Title").toString());
        assertEquals("Divine Recordings", blobProperties.get("ID3:Publisher").toString());
    }

    @Test
    public void itShouldWriteGivenMetadataInBinary() {
        // Get the document with PSD attached
        DocumentModel psdFile = BinaryMetadataServerInit.getFile(3, session);
        BlobHolder psdBlobHolder = psdFile.getAdapter(BlobHolder.class);

        // Check the content
        Map<String, Object> blobProperties = binaryMetadataService.readMetadata(psdBlobHolder.getBlob(), PSDMetadata);
        assertNotNull(blobProperties);
        assertEquals(2, blobProperties.size());
        assertEquals(100, blobProperties.get("EXIF:ImageHeight"));
        assertEquals("Adobe Photoshop CS4 Macintosh", blobProperties.get("EXIF:Software").toString());

        // Write a new content
        assertTrue(binaryMetadataService.writeMetadata(psdBlobHolder.getBlob(), inputPSDMetadata));

        // Check the content
        blobProperties = binaryMetadataService.readMetadata(psdBlobHolder
                .getBlob(), PSDMetadata);
        assertNotNull(blobProperties);
        assertEquals(2, blobProperties.size());
        assertEquals(200, blobProperties.get("EXIF:ImageHeight"));
        assertEquals("Nuxeo", blobProperties.get("EXIF:Software").toString());
    }

    @Test
    public void itShouldWriteDocPropertiesFromBinaryWithMapping() {
        // Fetch logs for binary service.
        Logger logger = Logger.getLogger(BinaryMetadataServiceImpl.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Layout layout = new SimpleLayout();
        Appender appender = new WriterAppender(layout, out);
        logger.addAppender(appender);

        // Get the document with PDF attached.
        DocumentModel pdfDoc = BinaryMetadataServerInit.getFile(1, session);

        // Copy into the document according to metadata mapping contribution.
        binaryMetadataService.writeMetadata(pdfDoc, session);

        // Check if the document has been overwritten by binary metadata.
        pdfDoc = BinaryMetadataServerInit.getFile(1, session);
        assertEquals("en-US", pdfDoc.getPropertyValue("dc:title"));
        assertEquals("OpenOffice.org 3.2", pdfDoc.getPropertyValue("dc:source"));
        assertEquals("30 kB", pdfDoc.getPropertyValue("dc:description"));

        // Check if logs are displayed.
        try {
            String logMsg = out.toString();
            assertNotNull(logMsg);
            assertEquals("WARN - Missing binary metadata descriptor with id "
                    + "'hello'. Or check your rule contribution with proper " + "metadataMapping-id.\n", logMsg);
        } finally {
            logger.removeAppender(appender);
        }
    }
}
