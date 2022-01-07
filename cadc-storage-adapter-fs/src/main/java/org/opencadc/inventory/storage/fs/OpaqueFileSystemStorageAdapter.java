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
 ************************************************************************
 */

package org.opencadc.inventory.storage.fs;

import ca.nrc.cadc.io.MultiBufferIO;
import ca.nrc.cadc.io.ReadException;
import ca.nrc.cadc.io.WriteException;
import ca.nrc.cadc.net.IncorrectContentChecksumException;
import ca.nrc.cadc.net.IncorrectContentLengthException;
import ca.nrc.cadc.net.ResourceNotFoundException;
import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.util.HexUtil;
import ca.nrc.cadc.util.MultiValuedProperties;
import ca.nrc.cadc.util.PropertiesReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.opencadc.inventory.InventoryUtil;
import org.opencadc.inventory.StorageLocation;
import org.opencadc.inventory.storage.ByteRange;
import org.opencadc.inventory.storage.NewArtifact;
import org.opencadc.inventory.storage.PutTransaction;
import org.opencadc.inventory.storage.StorageAdapter;
import org.opencadc.inventory.storage.StorageEngageException;
import org.opencadc.inventory.storage.StorageMetadata;

/**
 * An implementation of the storage adapter interface on a file system.
 * This adapter can work in two bucket modes, specified in the BucketMode
 * enumeration:  URI_BUCKET_BASED and URI_BASED.
 * In URI_BUCKET_BASED mode, files are organized by their artifact uriBucket and
 * filenames and paths have no relation to the artifact URI.  The contents
 * of the file system will not be recognizable without the inventory database
 * to provide the mapping.  Subsets of the bucket can be used to change the
 * scope of the tree seen in unsortedIterator.  Artifacts are decoupled from the
 * files in this mode so external artifact URIs may change without consequence.
 * In URI_BASED mode, files are organized by their artifact URI (path and filename).
 * The file system resembles the path and file hierarchy of the artifact URIs it holds.
 * In this mode, the storage location bucket is the path of the scheme-specific-part
 * of the artifact URI.  Subsets (that match a directory) of the storage buckets can
 * be used when calling unsortedIterator.  The items in the iterator in
 * this mode will contain the corresponding artifactURI.  It is not expected
 * that external artifact URIs are changed when this adapter is used.  If they do
 * they will become inconsistent with the items reported by this iterator.
 * In both modes, a null bucket parameter to unsortedIterator will result in the
 * iteration of all files in the file system root.
 * 
 * @author majorb
 *
 */
public class OpaqueFileSystemStorageAdapter implements StorageAdapter {
    private static final Logger log = Logger.getLogger(OpaqueFileSystemStorageAdapter.class);
    
    public static final String CONFIG_FILE = "cadc-storage-adapter-fs.properties";
    public static final String CONFIG_PROPERTY_ROOT = OpaqueFileSystemStorageAdapter.class.getPackage().getName() + ".baseDir";
    public static final String CONFIG_PROPERTY_BUCKET_LENGTH = OpaqueFileSystemStorageAdapter.class.getName() + ".bucketLength";

    public static final int MAX_BUCKET_LENGTH = 7;
            
    static final String ARTIFACTID_ATTR = "artifactID";
    static final String CHECKSUM_ATTR = "contentChecksum";
    static final String EXP_LENGTH_ATTR = "contentLength";
    
    private static final Long PT_MIN_BYTES = 1L;
    private static final Long PT_MAX_BYTES = null;
    
    private static final String TXN_FOLDER = "transaction";
    private static final String CONTENT_FOLDER = "content";

    private static final String MD5_CHECKSUM_SCHEME = "md5";
    private static final int CIRC_BUFFERS = 3;
    private static final int CIRC_BUFFERSIZE = 64 * 1024;

    // temporary hack to store intermediate digest state: cannot work across multiple JVMs aka with load balanced deployment
    private static final Map<String,MessageDigest> txnDigestStore = new TreeMap<>();
    
    final Path txnPath;
    final Path contentPath;
    private final int bucketLength;

    public OpaqueFileSystemStorageAdapter() {
        PropertiesReader pr = new PropertiesReader(CONFIG_FILE);
        MultiValuedProperties props = pr.getAllProperties();
        
        String rootVal = null;
        
        // get the configured root directory
        rootVal = props.getFirstPropertyValue(CONFIG_PROPERTY_ROOT);
        InventoryUtil.assertNotNull(OpaqueFileSystemStorageAdapter.class, CONFIG_PROPERTY_ROOT, rootVal);
        log.debug("root: " + rootVal);
        if (rootVal == null) {
            throw new IllegalStateException("failed to load " + CONFIG_PROPERTY_ROOT
                + " from " + CONFIG_FILE);
        }
        
        // in uriBucket mode get the bucket depth
        int bucketLen;
        String length = props.getFirstPropertyValue(CONFIG_PROPERTY_BUCKET_LENGTH);
        InventoryUtil.assertNotNull(OpaqueFileSystemStorageAdapter.class, CONFIG_PROPERTY_BUCKET_LENGTH, length);
        try {
            bucketLen = Integer.parseInt(length);
            if (bucketLen < 0 || bucketLen > MAX_BUCKET_LENGTH) {
                throw new IllegalStateException(CONFIG_PROPERTY_BUCKET_LENGTH + " must be in [1," + MAX_BUCKET_LENGTH + "], found " + bucketLen);
            }
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("invalid integer value: " + CONFIG_PROPERTY_BUCKET_LENGTH + " = " + length);
        }
        this.bucketLength = bucketLen;
        
        FileSystem fs = FileSystems.getDefault();
        Path root = fs.getPath(rootVal);
        this.contentPath = root.resolve(CONTENT_FOLDER);
        this.txnPath = root.resolve(TXN_FOLDER);

        init(root);
    }

    // for test code: OPAQUE mode
    public OpaqueFileSystemStorageAdapter(File rootDirectory, int bucketLen) {

        InventoryUtil.assertNotNull(FileSystemStorageAdapter.class, "rootDirectory", rootDirectory);

        if (bucketLen < 0 || bucketLen > MAX_BUCKET_LENGTH) {
            throw new IllegalStateException(CONFIG_PROPERTY_BUCKET_LENGTH + " must be in [1," + MAX_BUCKET_LENGTH + "], found " + bucketLen);
        }
        this.bucketLength = bucketLen;

        FileSystem fs = FileSystems.getDefault();
        Path root = fs.getPath(rootDirectory.getAbsolutePath());
        this.contentPath = root.resolve(CONTENT_FOLDER);
        this.txnPath = root.resolve(TXN_FOLDER);
        
        init(root);
    }

    void testDiag(String s) {
        log.warn(s + " - DIGEST_CACHE size: " + txnDigestStore.size());
        for (String key : txnDigestStore.keySet()) {
            log.warn(s + " - open transaction: " + key);
        }
    }
    
    private void init(Path root) {
        try {
            if (!Files.isDirectory(root)) {
                throw new IllegalArgumentException("root must be a directory");
            }
            if (!Files.isReadable(root) || (!Files.isWritable(root))) {
                throw new IllegalArgumentException("read-write permission required on root");
            }
            
            // Ensure  root/CONTENT_FOLDER and TXN_FOLDER exist and have correct permissions
            // Set Path elements for transaction and content directories
            if (!Files.exists(contentPath)) {
                Files.createDirectories(contentPath);
                log.debug("created content dir: " + contentPath);
            }
            if (!Files.isReadable(contentPath) || (!Files.isWritable(contentPath))) {
                throw new IllegalArgumentException("read-write permission required on content directory");
            }
            log.debug("validated content dir: " + contentPath);

            if (!Files.exists(txnPath)) {
                Files.createDirectories(txnPath);
                log.debug("created txn dir: " + txnPath);
            }
            if (!Files.isReadable(txnPath) || (!Files.isWritable(txnPath))) {
                throw new IllegalArgumentException("read-write permission required on transaction directory");
            }
            log.debug("validated txn dir: " + txnPath);

        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid root directory: " + root, e);
        } catch (IOException io) {
            throw new IllegalArgumentException(("Could not create content or transaction directory"), io);
        }
    }
    
    @Override
    public void get(StorageLocation storageLocation, OutputStream dest)
        throws ResourceNotFoundException, ReadException, WriteException, WriteException, StorageEngageException, TransientException {
        InventoryUtil.assertNotNull(OpaqueFileSystemStorageAdapter.class, "storageLocation", storageLocation);
        InventoryUtil.assertNotNull(OpaqueFileSystemStorageAdapter.class, "dest", dest);
        log.debug("get: " + storageLocation);

        Path path = storageLocationToPath(storageLocation);
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("not found: " + storageLocation);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("not a file: " + storageLocation);
        }
        InputStream source = null;
        try {
            source = Files.newInputStream(path, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new StorageEngageException("failed to create input stream for stored file: " + storageLocation, e);
        }

        MultiBufferIO io = new MultiBufferIO(CIRC_BUFFERS, CIRC_BUFFERSIZE);
        try {
            io.copy(source, dest);
        } catch (InterruptedException ex) {
            log.debug("get interrupted", ex);
        }
    }

    @Override
    public void get(StorageLocation storageLocation, OutputStream dest, ByteRange byteRange)
        throws ResourceNotFoundException, ReadException, WriteException, StorageEngageException, TransientException {
        InventoryUtil.assertNotNull(OpaqueFileSystemStorageAdapter.class, "storageLocation", storageLocation);
        InventoryUtil.assertNotNull(OpaqueFileSystemStorageAdapter.class, "dest", dest);
        InventoryUtil.assertNotNull(OpaqueFileSystemStorageAdapter.class, "byteRange", byteRange);
        log.debug("get: " + storageLocation + " " + byteRange);

        Path path = storageLocationToPath(storageLocation);
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("not found: " + storageLocation);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("not a file: " + storageLocation);
        }
        InputStream source = null;
        try {
            if (byteRange != null) {
                RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
                SortedSet<ByteRange> brs = new TreeSet<>();
                brs.add(byteRange);
                source = new PartialReadInputStream(raf, brs);
            } else {
                source = Files.newInputStream(path, StandardOpenOption.READ);
            }
        } catch (IOException e) {
            throw new StorageEngageException("failed to create input stream for stored file: " + storageLocation, e);
        }
        
        MultiBufferIO io = new MultiBufferIO(CIRC_BUFFERS, CIRC_BUFFERSIZE);
        try {
            io.copy(source, dest);
        } catch (InterruptedException ex) {
            log.debug("get interrupted", ex);
        }
    }
    
    @Override
    public StorageMetadata put(NewArtifact newArtifact, InputStream source, String transactionID)
        throws IncorrectContentChecksumException, IncorrectContentLengthException, 
            ReadException, WriteException,
            StorageEngageException, TransientException {
        InventoryUtil.assertNotNull(FileSystemStorageAdapter.class, "artifact", newArtifact);
        InventoryUtil.assertNotNull(FileSystemStorageAdapter.class, "source", source);

        Path txnTarget;
        MessageDigest txnDigest;
        try {
            boolean joinExisting = false;
            if (transactionID != null) {
                // validate
                UUID.fromString(transactionID);
                txnTarget = txnPath.resolve(transactionID);
                txnDigest = txnDigestStore.get(transactionID);
                if (!Files.exists(txnTarget) || txnDigest == null) {
                    throw new IllegalArgumentException("unknown transaction: " + transactionID);
                }
                // TODO check that artifact URI matches stored attr
                joinExisting = true;
            } else {
                String tmp = UUID.randomUUID().toString();
                txnTarget = txnPath.resolve(tmp);
                txnDigest = MessageDigest.getInstance("MD5");
            }

            if (!joinExisting && Files.exists(txnTarget)) {
                // unlikely: duplicate UUID in the txnpath directory
                throw new RuntimeException("BUG: txnTarget already exists: " + txnTarget);
            }
        } catch (InvalidPathException e) {
            throw new RuntimeException("BUG: invalid path: " + txnPath, e);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("failed to create MessageDigest: MD5");
        }
        log.debug("transaction: " + txnTarget + " transactionID: " + transactionID);
        
        Throwable throwable = null;
        URI checksum = null;
        
        Long length = null;
        
        try {
            OpenOption opt = StandardOpenOption.CREATE_NEW;
            if (transactionID != null) {
                opt = StandardOpenOption.APPEND;
            }
            MessageDigest md = (MessageDigest) txnDigest.clone();
            DigestOutputStream out = new DigestOutputStream(Files.newOutputStream(txnTarget, StandardOpenOption.WRITE, opt), txnDigest);
            MultiBufferIO io = new MultiBufferIO();
            if (transactionID != null) {
                long prevLength = 0L;
                try {
                    prevLength = Files.size(txnTarget);
                    log.debug("append starting at offset " + prevLength);
                    io.copy(source, out);
                    out.flush();
                    md = out.getMessageDigest();
                } catch (ReadException ex) {
                    // rollback to prevLength
                    RandomAccessFile raf = new RandomAccessFile(txnTarget.toFile(), "rws");
                    log.debug("rollback from " + raf.length() + " to " + prevLength + " after " + ex);
                    raf.setLength(prevLength);
                    length = Files.size(txnTarget);
                    log.debug("proceeding with transaction " + transactionID + " at offset " + length + " after failed input: " + ex);
                } catch (WriteException ex) {
                    log.debug("abort transactionID " + transactionID + " after failed write to back end: ", ex);
                    abortTransaction(transactionID);
                    throw ex;
                }
            } else {
                io.copy(source, out);
                out.flush();
                md = out.getMessageDigest();
            }

            // clone so we can persist the current state for resume
            MessageDigest curMD = (MessageDigest) md.clone();
            String md5Val = HexUtil.toHex(curMD.digest());
            checksum = URI.create(MD5_CHECKSUM_SCHEME + ":" + md5Val);
            log.debug("current checksum: " + checksum);
            length = Files.size(txnTarget);
            log.debug("current file size: " + length);
            
            if (transactionID != null && (newArtifact.contentLength == null || length < newArtifact.contentLength)) {
                // incomplete: no further content checks
                log.debug("incomplete put in transaction: " + transactionID + " - not verifying checksum");
            } else {
                boolean checksumProvided = newArtifact.contentChecksum != null && newArtifact.contentChecksum.getScheme().equals(MD5_CHECKSUM_SCHEME);
                if (checksumProvided) {
                    if (!newArtifact.contentChecksum.equals(checksum)) {
                        throw new IncorrectContentChecksumException(newArtifact.contentChecksum + " != " + checksum);
                    }
                }
                if (newArtifact.contentLength != null && !newArtifact.contentLength.equals(length)) {
                    if (checksumProvided) {
                        // likely bug in the client, throw a 400 instead
                        throw new IllegalArgumentException("length mismatch: " + newArtifact.contentLength + " != " + length
                            + " when checksum was correct: client BUG?");
                    }
                    throw new IncorrectContentLengthException(newArtifact.contentLength + " != " + length);
                }
            }

            // Set file attributes that must be recovered in iterator
            setFileAttribute(txnTarget, CHECKSUM_ATTR, checksum.toASCIIString());
            setFileAttribute(txnTarget, ARTIFACTID_ATTR, newArtifact.getArtifactURI().toASCIIString());
            
            StorageLocation storageLocation = pathToStorageLocation(txnTarget);
            
            if (transactionID != null) {
                log.debug("transaction uncommitted: " + transactionID + " " + storageLocation);
                // transaction will continue
                txnDigestStore.put(transactionID, md);
                if (length == 0L) {
                    return new StorageMetadata(storageLocation);
                }
                // current state
                return new StorageMetadata(storageLocation, checksum, length,
                        new Date(Files.getLastModifiedTime(txnTarget).toMillis()));
            }

            // create this before committing the file so constraints applied
            StorageMetadata metadata = new StorageMetadata(storageLocation, checksum, length,
                    new Date(Files.getLastModifiedTime(txnTarget).toMillis()));
            metadata.artifactURI = newArtifact.getArtifactURI();
            
            StorageMetadata ret = commit(metadata, txnTarget);
            txnTarget = null;
            return ret;
            
        } catch (ReadException | WriteException | IllegalArgumentException
            | IncorrectContentChecksumException | IncorrectContentLengthException e) {
            // pass through
            throw e;
        } catch (Throwable t) {
            throwable = t;
            log.error("put error", t);
            if (throwable instanceof IOException) {
                throw new StorageEngageException("put error", throwable);
            }
            // TODO: identify throwables that are transient
            throw new RuntimeException("Unexpected error", throwable);
        } finally {
            // txnTarget file still exists and not in a transaction: something went wrong
            if (txnTarget != null && transactionID == null) {
                try {
                    log.debug("Deleting transaction file.");
                    Files.delete(txnTarget);
                } catch (IOException e) {
                    log.error("Failed to delete transaction file", e);
                }
            }
        }
    }
    
    @Override
    public PutTransaction startTransaction(URI artifactURI, Long contentLength) throws StorageEngageException, TransientException {
        InventoryUtil.assertNotNull(FileSystemStorageAdapter.class, "artifactURI", artifactURI);
        try {
            String transactionID = UUID.randomUUID().toString();
            Path txnTarget = txnPath.resolve(transactionID);
            OutputStream  ostream = Files.newOutputStream(txnTarget, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
            ostream.close();
            
            MessageDigest md = MessageDigest.getInstance("MD5");
            MessageDigest curMD = (MessageDigest) md.clone();
            String md5Val = HexUtil.toHex(curMD.digest());
            URI checksum = URI.create(MD5_CHECKSUM_SCHEME + ":" + md5Val);
            txnDigestStore.put(transactionID, md);
            
            setFileAttribute(txnTarget, CHECKSUM_ATTR, checksum.toASCIIString());
            setFileAttribute(txnTarget, ARTIFACTID_ATTR, artifactURI.toASCIIString());
            if (contentLength != null) {
                setFileAttribute(txnTarget, EXP_LENGTH_ATTR, contentLength.toString());
            }
            return new PutTransaction(transactionID, PT_MIN_BYTES, PT_MAX_BYTES);
        } catch (IOException ex) {
            throw new StorageEngageException("failed to create transaction", ex);
        } catch (CloneNotSupportedException | NoSuchAlgorithmException ex) {
            throw new RuntimeException("BUG", ex);
        }
    }
    
    @Override
    public void abortTransaction(String transactionID) throws IllegalArgumentException, StorageEngageException, TransientException {
        InventoryUtil.assertNotNull(FileSystemStorageAdapter.class, "transactionID", transactionID);
        try {
            Path txnTarget = txnPath.resolve(transactionID);
            if (Files.exists(txnPath)) {
                Files.delete(txnTarget);
                txnDigestStore.remove(transactionID);
            } else {
                throw new IllegalArgumentException("unknown transaction: " + transactionID);
            }
        } catch (IOException ex) {
            throw new StorageEngageException("failed to create transaction", ex);
        }
    }
    
    @Override
    public PutTransaction getTransactionStatus(String transactionID)
        throws IllegalArgumentException, StorageEngageException, TransientException {
        InventoryUtil.assertNotNull(FileSystemStorageAdapter.class, "transactionID", transactionID);
        try {
            // validate
            UUID.fromString(transactionID);
            Path path = txnPath.resolve(transactionID);
            if (Files.exists(path)) {
                StorageMetadata ret = createStorageMetadata(txnPath, path);
                // txnPath does not have bucket dirs
                ret.getStorageLocation().storageBucket = InventoryUtil.computeBucket(ret.getStorageLocation().getStorageID(), bucketLength);
                PutTransaction pt = new PutTransaction(transactionID, PT_MIN_BYTES, PT_MAX_BYTES);
                pt.storageMetadata = ret;
                return pt;
            }
        } catch (InvalidPathException e) {
            throw new RuntimeException("BUG: invalid path: " + txnPath, e);
        }
        throw new IllegalArgumentException("unknown transaction: " + transactionID);
    }
    
    @Override
    public StorageMetadata commitTransaction(String transactionID)
        throws IllegalArgumentException, StorageEngageException, TransientException {
        PutTransaction pt = getTransactionStatus(transactionID);
        Path txnTarget = txnPath.resolve(transactionID); // again
        StorageMetadata ret = commit(pt.storageMetadata, txnTarget);
        txnDigestStore.remove(transactionID);
        return ret;
    }
    
    private StorageMetadata commit(StorageMetadata sm, Path txnTarget) throws StorageEngageException {
        
        try {
            Path contentTarget = storageLocationToPath(sm.getStorageLocation());
            // make sure parent (bucket) directories exist
            Path parent = contentTarget.getParent();
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (Files.exists(contentTarget)) {
                // since filename is a UUID this is fatal
                throw new RuntimeException("BUG: UUID collision on commit: " + sm.getStorageLocation());
            }

            // to atomic copy into content directory
            // TODO: make sure lastModified is not changed by this
            final Path result = Files.move(txnTarget, contentTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.debug("committed: " + result);

            return sm;
        } catch (IOException ex) {
            throw new StorageEngageException("failed to finish write to final location", ex);
        }
    }
    
    /**
     * Delete from storage the artifact identified by storageLocation.
     * @param storageLocation Identifies the artifact to delete.
     * 
     * @throws ResourceNotFoundException If the artifact could not be found.
     * @throws IOException If an unrecoverable error occurred.
     * @throws StorageEngageException If the adapter failed to interact with storage.
     * @throws TransientException If an unexpected, temporary exception occurred. 
     */
    @Override
    public void delete(StorageLocation storageLocation)
        throws ResourceNotFoundException, IOException, StorageEngageException, TransientException {
        InventoryUtil.assertNotNull(FileSystemStorageAdapter.class, "storageLocation", storageLocation);
        Path path = storageLocationToPath(storageLocation);
        Files.delete(path);
    }
    
    @Override
    public Iterator<StorageMetadata> iterator()
        throws StorageEngageException, TransientException {
        
        return new OpaqueIterator(contentPath, null);
    }
    
    @Override
    public Iterator<StorageMetadata> iterator(String storageBucket)
        throws StorageEngageException, TransientException {
        return new OpaqueIterator(contentPath, storageBucket);
    }
    

    // temporary location to write stream to
    Path createTmpFile() {
        return txnPath.resolve(UUID.randomUUID().toString());
    }
    
    // create from tmpfile in the txnPath to re-use UUID
    StorageLocation pathToStorageLocation(Path tmpfile) {
        // re-use the UUID from the tmpfile
        String sid = tmpfile.getFileName().toString();
        URI storageID = URI.create("uuid:" + sid);
        String storageBucket = InventoryUtil.computeBucket(storageID, bucketLength);
        StorageLocation loc = new StorageLocation(storageID);
        loc.storageBucket = storageBucket;
        log.debug("created: " + loc);
        return loc;
    }
    
    // generate destination path with bucket under contentPath
    Path storageLocationToPath(StorageLocation storageLocation) {
        StringBuilder path = new StringBuilder();
        String bucket = storageLocation.storageBucket;
        log.debug("bucket: " + bucket);
        InventoryUtil.assertNotNull(FileSystemStorageAdapter.class, "storageLocation.bucket", bucket);
        for (char c : bucket.toCharArray()) {
            path.append(c).append(File.separator);
        }
        path.append(storageLocation.getStorageID().getSchemeSpecificPart());
        log.debug("Resolving path in content : " + path.toString());
        Path ret = contentPath.resolve(path.toString());
        return ret;
    }

    // TODO: these methods would be used for a readable directory structure impl
    // split scheme+path components into storageBucket, filename into storageID
    StorageLocation createReadableStorageLocation(URI artifactURI) {
        // {scheme}/{path}/{to}/{filename} eg cadc/TEST/foo.fits
        StringBuilder path = new StringBuilder();
        path.append(artifactURI.getScheme()).append(File.separator);
        String ssp = artifactURI.getSchemeSpecificPart();
        int i = ssp.lastIndexOf("/");
        path.append(ssp.substring(0, i));
        String storageBucket = path.toString();
        URI storageID = URI.create("name:" + ssp.substring(i));
        StorageLocation loc = new StorageLocation(storageID);
        loc.storageBucket = storageBucket;
        log.debug("created: " + loc);
        return loc;
    }
    
    Path createReadableStorageLocationPath(StorageLocation storageLocation) {
        StringBuilder path = new StringBuilder();
        path.append(storageLocation.storageBucket).append(File.separator);
        path.append(storageLocation.getStorageID().getSchemeSpecificPart());
        log.debug("Resolving path in content : " + path.toString());
        Path ret = contentPath.resolve(path.toString());
        return ret;
    }
    
    public static void setFileAttribute(Path path, String attributeKey, String attributeValue) throws IOException {
        log.debug("setFileAttribute: " + path);
        if (attributeValue != null) {
            UserDefinedFileAttributeView udv = Files.getFileAttributeView(path,
                UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            attributeValue = attributeValue.trim();
            log.debug("attribute: " + attributeKey + " = " + attributeValue);
            ByteBuffer buf = ByteBuffer.wrap(attributeValue.getBytes(Charset.forName("UTF-8")));
            udv.write(attributeKey, buf);
        } // else: do nothing
    }

    // also used by OpaqueIterator 
    static StorageMetadata createStorageMetadata(Path base, Path p) {
        Path rel = base.relativize(p);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rel.getNameCount() - 1; i++) {
            sb.append(rel.getName(i));
        }
        String storageBucket = sb.toString();
        URI storageID = URI.create("uuid:" + rel.getFileName());
        
        // take care: if base is txnPath, then storageBucket is empty string
        
        log.debug("createStorageMetadata: " + storageBucket + "," + storageID);
        try {
            StorageLocation sloc = new StorageLocation(storageID);
            sloc.storageBucket = storageBucket;
            try {
                String csAttr = getFileAttribute(p, OpaqueFileSystemStorageAdapter.CHECKSUM_ATTR);
                String aidAttr = getFileAttribute(p, OpaqueFileSystemStorageAdapter.ARTIFACTID_ATTR);
                URI contentChecksum = new URI(csAttr);
                long contentLength = Files.size(p);
                StorageMetadata ret = new StorageMetadata(sloc, contentChecksum, contentLength, 
                        new Date(Files.getLastModifiedTime(p).toMillis()));
                // optional
                ret.artifactURI = new URI(aidAttr);
                return ret;
            } catch (FileSystemException | IllegalArgumentException | URISyntaxException ex) {
                return new StorageMetadata(sloc); // missing attrs: invalid stored object
            }
        } catch (IOException ex) {
            throw new RuntimeException("failed to recreate StorageMetadata: " + storageBucket + "," + storageID, ex);
        }
    }
    
    static String getFileAttribute(Path path, String attributeName) throws IOException {
        UserDefinedFileAttributeView udv = Files.getFileAttributeView(path,
            UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);

        int sz = udv.size(attributeName);
        ByteBuffer buf = ByteBuffer.allocate(2 * sz);
        udv.read(attributeName, buf);
        return new String(buf.array(), Charset.forName("UTF-8")).trim();
    }
}
