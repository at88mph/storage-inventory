/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2019.                            (c) 2019.
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
 ************************************************************************
 */

package org.opencadc.minoc;

import ca.nrc.cadc.db.ConnectionConfig;
import ca.nrc.cadc.db.DBConfig;
import ca.nrc.cadc.db.DBUtil;
import ca.nrc.cadc.net.HttpDelete;
import ca.nrc.cadc.net.HttpGet;
import ca.nrc.cadc.net.HttpUpload;
import ca.nrc.cadc.util.Log4jInit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.security.PrivilegedExceptionAction;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import javax.security.auth.Subject;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.opencadc.inventory.Artifact;
import org.opencadc.inventory.db.ArtifactDAO;
import org.opencadc.inventory.db.SQLGenerator;

/**
 * Test artifact replacement
 * 
 * @author majorb
 */
public class ReplaceArtifactTest extends MinocTest {
    
    private static final Logger log = Logger.getLogger(ReplaceArtifactTest.class);
    
    static {
        Log4jInit.setLevel("org.opencadc.minoc", Level.INFO);
    }
    
    ArtifactDAO dao;
    
    public ReplaceArtifactTest() throws Exception {
        super();
        
        DBConfig dbrc = new DBConfig();
        ConnectionConfig cc = dbrc.getConnectionConfig("MINOC_TEST", "cadctest");
        DBUtil.createJNDIDataSource("jdbc/minoc", cc);
        Map<String,Object> config = new TreeMap<>();
        config.put("jndiDataSourceName", "jdbc/minoc");
        config.put(SQLGenerator.class.getName(), SQLGenerator.class);
        config.put("invSchema", "inventory");
        config.put("genSchema", "inventory");
        
        this.dao = new ArtifactDAO();
        dao.setConfig(config);
    }
    
    @Test
    public void testReplaceFile() {
        try {
            
            Subject.doAs(userSubject, new PrivilegedExceptionAction<Object>() {
                public Object run() throws Exception {
            
                    final String data1 = "first artifact";
                    final String data2 = "second artifact";
                    URI artifactURI = URI.create("cadc:TEST/testReplaceFile");
                    URL artifactURL = new URL(filesURL + "/" + artifactURI.toString());
                    
                    // put initial file
                    InputStream in = new ByteArrayInputStream(data1.getBytes());
                    HttpUpload put = new HttpUpload(in, artifactURL);
                    put.setDigest(computeChecksumURI(data1.getBytes()));
                    put.run();
                    Assert.assertNull(put.getThrowable());
                    
                    // assert file and metadata
                    OutputStream out = new ByteArrayOutputStream();
                    HttpGet get = new HttpGet(artifactURL, out);
                    get.run();
                    Assert.assertNull(get.getThrowable());
                    URI digest = get.getDigest();
                    long contentLength = get.getContentLength();
                    Assert.assertEquals(computeChecksumURI(data1.getBytes()), digest);
                    Assert.assertEquals(data1.getBytes().length, contentLength);
                    
                    // replace with new data
                    in = new ByteArrayInputStream(data2.getBytes());
                    put = new HttpUpload(in, artifactURL);
                    put.setDigest(computeChecksumURI(data2.getBytes()));
                    put.run();
                    Assert.assertNull(put.getThrowable());
                    
                    // assert new file and metadata
                    out = new ByteArrayOutputStream();
                    get = new HttpGet(artifactURL, out);
                    get.run();
                    Assert.assertNull(get.getThrowable());
                    digest = get.getDigest();
                    contentLength = get.getContentLength();
                    Assert.assertEquals(computeChecksumURI(data2.getBytes()), digest);
                    Assert.assertEquals(data2.getBytes().length, contentLength);
                    
                    // delete
                    HttpDelete delete = new HttpDelete(artifactURL, false);
                    delete.run();
                    Assert.assertNull(delete.getThrowable());
                    
                    return null;
                }
            });
            
        } catch (Exception t) {
            log.error("unexpected throwable", t);
            Assert.fail("unexpected throwable: " + t);
        }
    }
    
    @Test
    public void testReplaceUnstoredfArtifact() {
        try {
            
            Subject.doAs(userSubject, new PrivilegedExceptionAction<Object>() {
                public Object run() throws Exception {
            
                    final String data2 = "second artifact";
                    
                    final URI artifactURI = URI.create("cadc:TEST/testReplaceUnstoredfArtifact");
                    final URL artifactURL = new URL(filesURL + "/" + artifactURI.toString());

                    Artifact prev = dao.get(artifactURI);
                    if (prev != null) {
                        log.info("cleanup: " + prev);
                        dao.delete(prev.getID());
                    }
                    
                    Artifact a = new Artifact(artifactURI, URI.create("md5:985b343f03d927ffb32e9de086d15730"), new Date(), 15L);
                    dao.put(a);
                    log.info("put: " + a);

                    long sleepWaitMillis = 1200L;
                    log.info("sleep millis: " + sleepWaitMillis);
                    Thread.sleep(sleepWaitMillis);
                    log.info("sleep millis OK: " + sleepWaitMillis);
                    
                    // try to overwrite with actual
                    InputStream in = new ByteArrayInputStream(data2.getBytes());
                    HttpUpload put = new HttpUpload(in, artifactURL);
                    put.setDigest(computeChecksumURI(data2.getBytes()));
                    put.run();
                    Assert.assertNull(put.getThrowable());
                    
                    // assert file and metadata
                    OutputStream out = new ByteArrayOutputStream();
                    HttpGet get = new HttpGet(artifactURL, out);
                    get.run();
                    Assert.assertNull(get.getThrowable());
                    URI digest = get.getDigest();
                    long contentLength = get.getContentLength();
                    Assert.assertEquals(computeChecksumURI(data2.getBytes()), digest);
                    Assert.assertEquals(data2.getBytes().length, contentLength);
                    
                    Artifact actual = dao.get(artifactURI); // overwrite == new Artifact: get by URI
                    log.info("get: " + actual);
                    Assert.assertNotNull(actual);
                    Assert.assertNotNull("storageLocation", actual.storageLocation);
                    Assert.assertTrue(a.getContentLastModified().before(actual.getContentLastModified()));
                    Assert.assertEquals(digest, actual.getContentChecksum());
                    Assert.assertEquals(contentLength, actual.getContentLength().longValue());
                    
                    // delete
                    HttpDelete delete = new HttpDelete(artifactURL, false);
                    delete.run();
                    Assert.assertNull(delete.getThrowable());
                    
                    return null;
                }
            });
            
        } catch (Exception t) {
            log.error("unexpected throwable", t);
            Assert.fail("unexpected throwable: " + t);
        }
    }
    
}
