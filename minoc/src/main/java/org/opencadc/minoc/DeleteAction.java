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

import ca.nrc.cadc.db.TransactionManager;
import ca.nrc.cadc.net.ResourceNotFoundException;
import org.apache.log4j.Logger;
import org.opencadc.inventory.Artifact;
import org.opencadc.inventory.DeletedArtifactEvent;
import org.opencadc.inventory.db.DeletedArtifactEventDAO;
import org.opencadc.inventory.db.EntityNotFoundException;
import org.opencadc.inventory.db.ObsoleteStorageLocation;
import org.opencadc.inventory.db.ObsoleteStorageLocationDAO;
import org.opencadc.permissions.WriteGrant;

/**
 * Interface with storage and inventory to delete an artifact.
 *
 * @author majorb
 */
public class DeleteAction extends ArtifactAction {
    
    private static final Logger log = Logger.getLogger(DeleteAction.class);

    /**
     * Default, no-arg constructor.
     */
    public DeleteAction() {
        super();
    }

    /**
     * Delete the artifact. 
     */
    @Override
    public void doAction() throws Exception {
        
        checkWritable();
        initAndAuthorize(WriteGrant.class);
        
        Artifact existing = artifactDAO.get(artifactURI);
        if (existing == null) {
            throw new ResourceNotFoundException("not found: " + artifactURI);
        }
        
        DeletedArtifactEventDAO eventDAO = new DeletedArtifactEventDAO(artifactDAO);
        TransactionManager txnMgr = artifactDAO.getTransactionManager();
        
        try {
            log.debug("starting transaction");
            txnMgr.startTransaction();
            log.debug("start txn: OK");
            
            boolean locked = false;
            while (existing != null && !locked) {
                try { 
                    artifactDAO.lock(existing);
                    locked = true;
                } catch (EntityNotFoundException ex) {
                    // entity deleted
                    existing = artifactDAO.get(artifactURI);
                }
            }
            if (existing == null) {
                // artifact deleted while trying to get a lock
                throw new ResourceNotFoundException("not found: " + artifactURI);
            }
            
            DeletedArtifactEvent deletedArtifact = new DeletedArtifactEvent(existing.getID());
            eventDAO.put(deletedArtifact);
            artifactDAO.delete(existing.getID());
            ObsoleteStorageLocation dsl = null;
            ObsoleteStorageLocationDAO locDAO = new ObsoleteStorageLocationDAO(artifactDAO);
            if (existing.storageLocation != null) {
                dsl = new ObsoleteStorageLocation(existing.storageLocation);
                locDAO.put(dsl);
            }
            
            log.debug("committing transaction");
            txnMgr.commitTransaction();
            log.debug("commit txn: OK");
            
            // this block could be passed off to a thread so request completes
            if (dsl != null) {
                log.debug("deleting from storage...");
                storageAdapter.delete(existing.storageLocation);
                log.debug("delete from storage: OK");
                // obsolete tracker record no longer needed
                locDAO.delete(dsl.getID());
            }
        } catch (Exception e) {
            log.error("failed to delete " + artifactURI, e);
            txnMgr.rollbackTransaction();
            log.debug("rollback txn: OK");
            throw e;
        } finally {
            if (txnMgr.isOpen()) {
                log.error("BUG - open transaction in finally");
                txnMgr.rollbackTransaction();
                log.error("rollback txn: OK");
            }
        }
    }

}
