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

package org.opencadc.luskan.ws;

import ca.nrc.cadc.auth.AuthMethod;
import ca.nrc.cadc.db.DBUtil;
import ca.nrc.cadc.net.ResourceNotFoundException;
import ca.nrc.cadc.reg.AccessURL;
import ca.nrc.cadc.reg.Capabilities;
import ca.nrc.cadc.reg.Capability;
import ca.nrc.cadc.reg.Interface;
import ca.nrc.cadc.reg.Standards;
import ca.nrc.cadc.reg.client.LocalAuthority;
import ca.nrc.cadc.reg.client.RegistryClient;
import ca.nrc.cadc.rest.RestAction;
import ca.nrc.cadc.tap.schema.InitDatabaseTS;
import ca.nrc.cadc.uws.server.impl.InitDatabaseUWS;
import ca.nrc.cadc.vosi.AvailabilityPlugin;
import ca.nrc.cadc.vosi.AvailabilityStatus;
import ca.nrc.cadc.vosi.avail.CheckCertificate;
import ca.nrc.cadc.vosi.avail.CheckDataSource;
import ca.nrc.cadc.vosi.avail.CheckException;
import ca.nrc.cadc.vosi.avail.CheckResource;
import ca.nrc.cadc.vosi.avail.CheckWebService;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.NoSuchElementException;
import javax.sql.DataSource;
import org.apache.log4j.Logger;

import org.opencadc.luskan.InitLuskanSchemaContent;


/**
 *
 * @author adriand
 */
public class StorageTapService implements AvailabilityPlugin {

    private static final Logger log = Logger.getLogger(StorageTapService.class);
    private static final File SERVOPS_PEM_FILE = new File(System.getProperty("user.home") + "/.ssl/cadcproxy.pem");


    private static final String TAP_TEST = "select schema_name from tap_schema.schemas11 where schema_name='tap_schema'";
    private static final String UWS_TEST = "select jobID from uws.Job limit 1";

    private String appName;

    public StorageTapService() {
    }

    @Override
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    @Override
    public AvailabilityStatus getStatus() {
        boolean isGood = true;
        String note = "service is accepting queries";
        try {
            String state = getState();
            if (RestAction.STATE_OFFLINE.equals(state)) {
                return new AvailabilityStatus(false, null, null, null, RestAction.STATE_OFFLINE_MSG);
            }
            if (RestAction.STATE_READ_ONLY.equals(state)) {
                return new AvailabilityStatus(false, null, null, null, RestAction.STATE_READ_ONLY_MSG);
            }

            DataSource tapadm = DBUtil.findJNDIDataSource("jdbc/tapadm");
            InitDatabaseTS tsi = new InitDatabaseTS(tapadm, null, "tap_schema");
            tsi.doInit();

            DataSource uws = DBUtil.findJNDIDataSource("jdbc/tapadm");
            InitDatabaseUWS uwsi = new InitDatabaseUWS(uws, null, "uws");
            uwsi.doInit();

            // check for a certficate needed to perform network ops
            CheckCertificate checkCert = new CheckCertificate(SERVOPS_PEM_FILE);
            checkCert.check();
            log.info("cert check ok");

            // create the TAP schema
            InitLuskanSchemaContent lsc = new InitLuskanSchemaContent(tapadm, null, "tap_schema");
            lsc.doInit();

            // run a couple of queries
            CheckResource cr = new CheckDataSource("jdbc/tapuser", TAP_TEST);
            cr.check();

            // TODO - These do not work for intTest unless the testing environment deploys these services.
            String url = getAvailabilityForLocal(Standards.CRED_PROXY_10);
            CheckResource checkResource = new CheckWebService(url);
            //checkResource.check();

            url = getAvailabilityForLocal(Standards.UMS_USERS_01);
            checkResource = new CheckWebService(url);
            //checkResource.check();

            url = getAvailabilityForLocal(Standards.GMS_SEARCH_01);
            checkResource = new CheckWebService(url);
            checkResource.check();

        } catch (CheckException ce) {
            // tests determined that the resource is not working
            isGood = false;
            note = ce.getMessage();
        } catch (Throwable throwable) {
            // the test itself failed
            StringBuilder sb = new StringBuilder();
            sb.append("test failed because: ");
            while (throwable != null) {
                sb.append(throwable.getMessage()).append(": ");
                throwable = throwable.getCause();
            }
            log.error(sb.toString());
            isGood = false;
            note = sb.toString();
        }
        return new AvailabilityStatus(isGood, null, null, null, note);
    }

    private String getAvailabilityForLocal(URI standardID)
        throws ResourceNotFoundException {
        LocalAuthority localAuthority = new LocalAuthority();
        RegistryClient reg = new RegistryClient();

        URI resourceID;
        try {
            resourceID = localAuthority.getServiceURI(standardID.toString());
        } catch (NoSuchElementException e) {
            String message = String.format("service URI not found in LocalAuthority for standardID %s", standardID);
            throw new ResourceNotFoundException(message);
        }

        Capabilities capabilities;
        try {
            capabilities = reg.getCapabilities(resourceID);
        } catch (IOException e) {
            String message = String.format("io error getting capabilities for resourceID %s, standardID %s, because %s",
                                           resourceID, standardID, e.getMessage());
            throw new ResourceNotFoundException(message);
        }

        Capability capability = capabilities.findCapability(Standards.VOSI_AVAILABILITY);
        if (capability == null) {
            String message =
                String.format("service %s not does not provide %s", standardID, Standards.VOSI_AVAILABILITY);
            throw new UnsupportedOperationException(message);
        }

        Interface anInterface = capability.findInterface(AuthMethod.ANON);
        if (anInterface == null) {
            String message = String.format(
                "unexpected: service %s capability %s does not support auth: %s",
                standardID, capability, AuthMethod.ANON);
            throw new RuntimeException(message);
        }

        AccessURL accessURL = anInterface.getAccessURL();
        if (accessURL == null) {
            String message = String.format(
                "AccessURL not found in interface %s, capability %s, authMethod %s, standard %s, resourceID %s, "
                    + "standardID %s", anInterface, capability, AuthMethod.ANON, Standards.VOSI_AVAILABILITY,
                resourceID, standardID);
            throw new ResourceNotFoundException(message);
        }
        return accessURL.getURL().toExternalForm();
    }

    @Override
    public void setState(String state) {
        String key = appName + RestAction.STATE_MODE_KEY;
        if (RestAction.STATE_OFFLINE.equalsIgnoreCase(state)) {
            System.setProperty(key, RestAction.STATE_OFFLINE);
        //} else if (RestAction.STATE_READ_ONLY.equalsIgnoreCase(state)) {
        //    System.setProperty(key, RestAction.STATE_READ_ONLY);
        } else if (RestAction.STATE_READ_WRITE.equalsIgnoreCase(state)) {
            System.setProperty(key, RestAction.STATE_READ_WRITE);
        } else {
            throw new IllegalArgumentException("invalid state: " + state
                + " expected: " + RestAction.STATE_READ_WRITE + "|" + RestAction.STATE_OFFLINE);
        }
        log.info("WebService state changed: " + key + "=" + state + " [OK]");
    }
    
    @Override
    public boolean heartbeat() throws RuntimeException {
        return true;
    }

    private String getState() {
        String key = appName + RestAction.STATE_MODE_KEY;
        String ret = System.getProperty(key);
        if (ret == null) {
            return RestAction.STATE_READ_WRITE;
        }
        return ret;
    }

}
