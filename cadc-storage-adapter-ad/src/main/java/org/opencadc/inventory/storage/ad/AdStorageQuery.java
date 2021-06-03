/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2020.                            (c) 2020.
 *  Government of Canada                 Gouvernement du Canada
 *  National Research Council            Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 *  All rights reserved                  Tous droits réservés
 *
 *  NRC disclaims any warranties,        Le CNRC dénie toute garantie
 *  expressed, implied, or               énoncée, implicite ou légale,
 *  statutory, of any kind with          de quelque nature que ce
 *  respect to the software,             soit, concernant le logiciel,
 *  including without limitation         y compris sans restriction
 *  any warranty of merchantability      toute garantie de valeur
 *  or fitness for a particular          marchande ou de pertinence
 *  purpose. NRC shall not be            pour un usage particulier.
 *  liable in any event for any          Le CNRC ne pourra en aucun cas
 *  damages, whether direct or           être tenu responsable de tout
 *  indirect, special or general,        dommage, direct ou indirect,
 *  consequential or incidental,         particulier ou général,
 *  arising from the use of the          accessoire ou fortuit, résultant
 *  software.  Neither the name          de l'utilisation du logiciel. Ni
 *  of the National Research             le nom du Conseil National de
 *  Council of Canada nor the            Recherches du Canada ni les noms
 *  names of its contributors may        de ses  participants ne peuvent
 *  be used to endorse or promote        être utilisés pour approuver ou
 *  products derived from this           promouvoir les produits dérivés
 *  software without specific prior      de ce logiciel sans autorisation
 *  written permission.                  préalable et particulière
 *                                       par écrit.
 *
 *  This file is part of the             Ce fichier fait partie du projet
 *  OpenCADC project.                    OpenCADC.
 *
 *  OpenCADC is free software:           OpenCADC est un logiciel libre ;
 *  you can redistribute it and/or       vous pouvez le redistribuer ou le
 *  modify it under the terms of         modifier suivant les termes de
 *  the GNU Affero General Public        la “GNU Affero General Public
 *  License as published by the          License” telle que publiée
 *  Free Software Foundation,            par la Free Software Foundation
 *  either version 3 of the              : soit la version 3 de cette
 *  License, or (at your option)         licence, soit (à votre gré)
 *  any later version.                   toute version ultérieure.
 *
 *  OpenCADC is distributed in the       OpenCADC est distribué
 *  hope that it will be useful,         dans l’espoir qu’il vous
 *  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
 *  without even the implied             GARANTIE : sans même la garantie
 *  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
 *  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
 *  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
 *  General Public License for           Générale Publique GNU Affero
 *  more details.                        pour plus de détails.
 *
 *  You should have received             Vous devriez avoir reçu une
 *  a copy of the GNU Affero             copie de la Licence Générale
 *  General Public License along         Publique GNU Affero avec
 *  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
 *  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
 *                                       <http://www.gnu.org/licenses/>.
 *
 *  $Revision: 4 $
 *
 ************************************************************************
 */

package org.opencadc.inventory.storage.ad;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.opencadc.inventory.InventoryUtil;
import org.opencadc.inventory.StorageLocation;
import org.opencadc.inventory.storage.StorageMetadata;
import org.opencadc.tap.TapRowMapper;

/**
 * Provide query and RowMapper instance for grabbing data from ad.
 * RowMapper maps from the AD archive_files table to a StorageMetadata object.
 */
public class AdStorageQuery {
    private static final Logger log = Logger.getLogger(AdStorageQuery.class);

    private static final String QTMPL = "SELECT archiveName, fileName, uri, inventoryURI, contentMD5, fileSize,"
            + " contentEncoding, contentType, ingestDate"
            + " FROM archive_files WHERE archiveName = '%s' ORDER BY fileName ASC, ingestDate DESC";

    private String query;
    
    private static String MD5_ENCODING_SCHEME = "md5:";

    AdStorageQuery(String storageBucket) {
        InventoryUtil.assertNotNull(AdStorageQuery.class, "storageBucket", storageBucket);
        this.query = String.format(this.QTMPL, storageBucket);
    }

    public TapRowMapper<StorageMetadata> getRowMapper() {
        return new AdStorageMetadataRowMapper();
    }

    class AdStorageMetadataRowMapper implements TapRowMapper<StorageMetadata> {

        public AdStorageMetadataRowMapper() { }

        @Override
        public StorageMetadata mapRow(List<Object> row) {
            Iterator i = row.iterator();

            String storageBucket = (String) i.next();
            String fname = (String) i.next();
            URI uri = (URI) i.next();
            // chose best storageID
            URI sid = URI.create("ad:" + storageBucket + "/" + fname);
            if ("mast".equals(uri.getScheme())) {
                sid = uri;
            }
            final URI storageID = sid;
            
            URI artifactURI = (URI) i.next();
            if (artifactURI == null) {
                log.warn(storageID + " has null inventoryURI: SKIP");
                return null;
            }
            
            // archive_files.contentMD5 is just the hex value
            URI contentChecksum = null;
            String hex = (String) i.next();
            try {
                contentChecksum = new URI(MD5_ENCODING_SCHEME + hex);
                InventoryUtil.assertValidChecksumURI(AdStorageQuery.class, "contentChecksum", contentChecksum);
            } catch (IllegalArgumentException | URISyntaxException u) {
                log.warn(storageID + " has invalid HEX md5sum - " + hex + ": SKIP");
                return null;
            }
            
            // archive_files.fileSize
            Long contentLength = (Long) i.next();
            if (contentLength == null) {
                log.warn(storageID + " has null fileSize: SKIP");
                return null;
            }

            StorageLocation storageLocation = new StorageLocation(storageID);
            storageLocation.storageBucket = storageBucket;

            StorageMetadata storageMetadata = new StorageMetadata(storageLocation, contentChecksum, contentLength);
            storageMetadata.artifactURI = artifactURI;

            // optional values
            storageMetadata.contentEncoding = (String) i.next();
            storageMetadata.contentType = (String) i.next();
            storageMetadata.contentLastModified = (Date) i.next();

            return storageMetadata;
        }
    }

    public String getQuery() {
        return this.query;
    }
}

