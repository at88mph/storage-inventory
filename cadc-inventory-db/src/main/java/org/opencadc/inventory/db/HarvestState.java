/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2024.                            (c) 2024.
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

package org.opencadc.inventory.db;

import java.net.URI;
import java.util.Date;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.opencadc.inventory.Entity;
import org.opencadc.inventory.InventoryUtil;

/**
 *
 * @author pdowler
 */
public class HarvestState extends Entity {
    private static final Logger log = Logger.getLogger(HarvestState.class);

    private String name;
    private URI resourceID;
    
    /**
     * The timestamp of the last successfully harvested entity.
     */
    public Date curLastModified;

    /**
     * The ID of the last successfully harvested entity.
     */
    public UUID curID;
    
    /**
     * The ID of the current running instance. This is optional and only used by applications 
     * that share workload between instances.
     */
    public UUID instanceID;
    
    public HarvestState(String name, URI resourceID) { 
        super();
        InventoryUtil.assertNotNull(HarvestState.class, "name", name);
        InventoryUtil.assertNotNull(HarvestState.class, "resourceID", resourceID);
        this.name = name;
        this.resourceID = resourceID;
    }
    
    // package access: reconstruct an instance from serialised state.
    HarvestState(UUID id, String name, URI resourceID) { 
        super(id);
        InventoryUtil.assertNotNull(HarvestState.class, "name", name);
        InventoryUtil.assertNotNull(HarvestState.class, "resourceID", resourceID);
        this.name = name;
        this.resourceID = resourceID;
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        InventoryUtil.assertNotNull(HarvestState.class, "name", name);
        this.name = name;
    }
    
    public URI getResourceID() {
        return resourceID;
    }

    public void setResourceID(URI resourceID) {
        InventoryUtil.assertNotNull(HarvestState.class, "resourceID", resourceID);
        this.resourceID = resourceID;
    }

    @Override
    public String toString() {
        if (instanceID != null) {
            return HarvestState.class.getSimpleName() + "[" + instanceID + "," + name + "," + resourceID + "]";
        }
        return HarvestState.class.getSimpleName() + "[" + name + "," + resourceID + "]";
    }
}
