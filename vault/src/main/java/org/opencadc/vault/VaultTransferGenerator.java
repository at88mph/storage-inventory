/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2022.                            (c) 2022.
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

package org.opencadc.vault;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.auth.IdentityManager;
import ca.nrc.cadc.net.ResourceNotFoundException;
import ca.nrc.cadc.reg.Standards;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.vosi.Availability;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.security.auth.Subject;
import org.apache.log4j.Logger;
import org.opencadc.inventory.db.ArtifactDAO;
import org.opencadc.inventory.transfer.ProtocolsGenerator;
import org.opencadc.inventory.transfer.StorageSiteAvailabilityCheck;
import org.opencadc.inventory.transfer.StorageSiteRule;
import org.opencadc.permissions.TokenTool;
import org.opencadc.vospace.ContainerNode;
import org.opencadc.vospace.DataNode;
import org.opencadc.vospace.LinkingException;
import org.opencadc.vospace.Node;
import org.opencadc.vospace.NodeNotFoundException;
import org.opencadc.vospace.VOSURI;
import org.opencadc.vospace.server.PathResolver;
import org.opencadc.vospace.server.auth.VOSpaceAuthorizer;
import org.opencadc.vospace.server.transfers.TransferGenerator;
import org.opencadc.vospace.transfer.Direction;
import org.opencadc.vospace.transfer.Protocol;
import org.opencadc.vospace.transfer.Transfer;

/**
 *
 * @author pdowler
 */
public class VaultTransferGenerator implements TransferGenerator {
    private static final Logger log = Logger.getLogger(VaultTransferGenerator.class);

    private final NodePersistenceImpl nodePersistence;
    private final VOSpaceAuthorizer authorizer;
    private final ArtifactDAO artifactDAO;
    private final TokenTool tokenTool;
    private final boolean preventNotFound;
    
    private Map<URI, StorageSiteRule> siteRules = new HashMap<>();
    private Map<URI, Availability> siteAvailabilities;
    
    @SuppressWarnings("unchecked")
    public VaultTransferGenerator(NodePersistenceImpl nodePersistence, ArtifactDAO artifactDAO, TokenTool tokenTool, boolean preventNotFound) {
        this.nodePersistence = nodePersistence;
        this.authorizer = new VOSpaceAuthorizer(nodePersistence);
        this.artifactDAO = artifactDAO;
        this.tokenTool = tokenTool;
        this.preventNotFound = preventNotFound;
        
        // TODO: get appname from ???
        String siteAvailabilitiesKey = "vault" + "-" + StorageSiteAvailabilityCheck.class.getName();
        log.debug("siteAvailabilitiesKey: " + siteAvailabilitiesKey);
        try {
            Context initContext = new InitialContext();
            this.siteAvailabilities = (Map<URI, Availability>) initContext.lookup(siteAvailabilitiesKey);
            log.debug("found siteAvailabilities in JNDI: " + siteAvailabilitiesKey + " = " + siteAvailabilities);
            for (Map.Entry<URI, Availability> me: siteAvailabilities.entrySet()) {
                log.debug("found: " + me.getKey() + " = " + me.getValue());
            }
        } catch (NamingException e) {
            throw new IllegalStateException("JNDI lookup error", e);
        }
    }

    @Override
    public List<Protocol> getEndpoints(VOSURI target, Transfer transfer, Job job, List<Parameter> additionalParams) throws Exception {
        log.debug("getEndpoints: " + target);
        if (target == null) {
            throw new IllegalArgumentException("target is required");
        }
        if (transfer == null) {
            throw new IllegalArgumentException("transfer is required");
        }
        List<Protocol> ret = null;
        try {
            Direction dir = transfer.getDirection();
            PathResolver ps = new PathResolver(nodePersistence, authorizer, true);
            Node n = ps.getNode(target.getParentURI().getPath());
            // assume not null and Container already checked by caller (TransferRunner)
            ContainerNode parent = (ContainerNode) n;
            Node node = nodePersistence.get(parent, target.getName());

            Subject currentSubject = AuthenticationUtil.getCurrentSubject();
            if (Direction.pushToVoSpace.equals(dir) && node == null) {
                // create new data node?? this currently does not happen because the library
                // create DataNode the way that CreateNodeAction would
                //ret = handleDataNode(dn, transfer, currentSubject);
                throw new RuntimeException("BUG: expected DataNode to be created already: " + target.getPath());
            } else if (node instanceof DataNode) {
                DataNode dn = (DataNode) node;
                ret = handleDataNode(dn, transfer, currentSubject);
            } else {
                throw new UnsupportedOperationException(node.getClass().getSimpleName() + " transfer "
                    + target.getPath());
            }
        } catch (NodeNotFoundException ex) {
            throw new FileNotFoundException(target.getPath());
        } catch (LinkingException ex) {
            throw new RuntimeException("OOPS: failed to resolve link?", ex);
        }
        return ret;
    }
    
    private List<Protocol> handleDataNode(DataNode node, Transfer trans, Subject s) 
            throws IOException, ResourceNotFoundException {
        log.debug("handleDataNode: " + node);
        
        IdentityManager im = AuthenticationUtil.getIdentityManager();
        Subject caller = AuthenticationUtil.getCurrentSubject();
        Object userObject = im.toOwner(caller);
        String callingUser = (userObject == null ? null : userObject.toString());
        
        ProtocolsGenerator pg = new ProtocolsGenerator(artifactDAO, siteAvailabilities, siteRules);
        pg.tokenGen = tokenTool;
        pg.user = callingUser;
        pg.requirePreauthAnon = true;
        pg.preventNotFound = preventNotFound;
        
        Transfer artifactTrans = new Transfer(node.storageID, trans.getDirection());
        for (Protocol p : trans.getProtocols()) {
            log.debug("requested protocol: " + p);
            URI secM = p.getSecurityMethod();
            if (secM == null || secM.equals(Standards.SECURITY_METHOD_ANON)) {
                artifactTrans.getProtocols().add(p);
            }
        }
        
        try {
            List<Protocol> ret = pg.getProtocols(artifactTrans);
            log.debug("generated urls: " + ret.size());
            for (Protocol p : ret) {
                log.debug(p.getEndpoint() + " using " + p.getSecurityMethod());
            }
            return ret;
        } catch (ResourceNotFoundException ex) {
            return new ArrayList<Protocol>();
        }
    }
}
