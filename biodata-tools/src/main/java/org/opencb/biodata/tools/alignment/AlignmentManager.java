/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.biodata.tools.alignment;

import ga4gh.Reads;
import htsjdk.samtools.*;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.seekablestream.SeekableStreamFactory;
import htsjdk.samtools.util.Log;
import org.ga4gh.models.ReadAlignment;
import org.opencb.biodata.models.core.Region;
import org.opencb.biodata.tools.alignment.iterators.AlignmentIterator;
import org.opencb.biodata.tools.alignment.iterators.AvroIterator;
import org.opencb.biodata.tools.alignment.iterators.ProtoIterator;
import org.opencb.biodata.tools.alignment.iterators.SamRecordIterator;
import org.opencb.biodata.tools.alignment.stats.AlignmentGlobalStats;
import org.opencb.biodata.tools.alignment.stats.SamRecordAlignmentGlobalStatsCalculator;
import org.opencb.biodata.tools.alignment.tasks.RegionCoverage;
import org.opencb.biodata.tools.alignment.tasks.SamRecordRegionCoverageCalculator;
import org.opencb.commons.utils.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by imedina on 14/09/15.
 */
public class AlignmentManager {

    private Path input;
    private SamReader samReader;

    private static final int MAX_NUM_RECORDS = 50000;

    public AlignmentManager() {
    }

    public AlignmentManager(Path input) throws IOException {
        FileUtils.checkFile(input);
        this.input = input;

        SamReaderFactory srf = SamReaderFactory.make();
        srf.validationStringency(ValidationStringency.LENIENT);
        this.samReader = srf.open(SamInputResource.of(input.toFile()));
    }

    /**
     * Creates a index file for the BAM or CRAM input file.
     * @return The path of the index file.
     * @throws IOException
     */
    public Path createIndex() throws IOException {
        Path indexPath = input.getParent().resolve(input.getFileName().toString() + ".bai");
        return createIndex(indexPath);
    }

    /**
     * Creates a BAM/CRAM index file.
     * @param outputIndex The index created.
     * @return
     * @throws IOException
     */
    public Path createIndex(Path outputIndex) throws IOException {
        FileUtils.checkDirectory(outputIndex.toAbsolutePath().getParent(), true);

        SamReaderFactory srf = SamReaderFactory.make().enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS);
        srf.validationStringency(ValidationStringency.LENIENT);
        try (SamReader reader = srf.open(SamInputResource.of(input.toFile()))) {

            // Files need to be sorted by coordinates to create the index
            SAMFileHeader.SortOrder sortOrder = reader.getFileHeader().getSortOrder();
            if (!sortOrder.equals(SAMFileHeader.SortOrder.coordinate)) {
                throw new IOException("Expected sorted file. File '" + input.toString()
                        + "' is not sorted by coordinates (" + sortOrder.name() + ")");
            }

            if (reader.type().equals(SamReader.Type.BAM_TYPE)) {
                BAMIndexer.createIndex(reader, outputIndex.toFile(), Log.getInstance(AlignmentManager.class));
            } else {
                if (reader.type().equals(SamReader.Type.CRAM_TYPE)) {
                    // TODO This really needs to be tested!
                    SeekableStream streamFor = SeekableStreamFactory.getInstance().getStreamFor(input.toString());
                    CRAMIndexer.createIndex(streamFor, outputIndex.toFile(), Log.getInstance(AlignmentManager.class));
                } else {
                    throw new IOException("This is not a BAM or CRAM file. SAM files cannot be indexed");
                }
            }
        }
        return outputIndex;
    }

    /**
     * This method aims to provide a very simple, safe and quick way of accessing to a small fragment of the BAM/CRAM file.
     * This must not be used in production for reading big data files. It returns a maximum of 10,000 SAM records.
     *
     * @param region@return
     * @throws IOException
     */
    public List<ReadAlignment> query(Region region) throws Exception {
        return query(region, new AlignmentOptions(), null, ReadAlignment.class);
    }

    public List<ReadAlignment> query(Region region, AlignmentOptions options) throws Exception {
        return query(region, options, null, ReadAlignment.class);
    }

    public List<ReadAlignment> query(Region region, AlignmentOptions options, AlignmentFilters filters) throws Exception {
        return query(region, options, filters, ReadAlignment.class);
    }

    public List<ReadAlignment> query() throws Exception {
        return query(null, new AlignmentOptions(), null, ReadAlignment.class);
    }

    public List<ReadAlignment> query(AlignmentOptions options) throws Exception {
        return query(null, options, null, ReadAlignment.class);
    }

    public List<ReadAlignment> query(AlignmentOptions options, AlignmentFilters filters) throws Exception {
        return query(null, options, filters, ReadAlignment.class);
    }

    public <T> List<T> query(AlignmentOptions options, AlignmentFilters filters, Class<T> clazz) throws Exception {
        return query(null, options, filters, clazz);
    }

    public <T> List<T> query(Region region, AlignmentOptions alignmentOptions, AlignmentFilters filters, Class<T> clazz) throws Exception {
        if (alignmentOptions == null) {
            alignmentOptions = new AlignmentOptions();
        }

        int maxNumberRecords = (alignmentOptions.getLimit() > 0 && alignmentOptions.getLimit() <= MAX_NUM_RECORDS)
                ? alignmentOptions.getLimit()
                : MAX_NUM_RECORDS;
        List<T> results = new ArrayList<>(maxNumberRecords);
        AlignmentIterator<T> alignmentIterator;
        alignmentIterator = (region != null)
                ? iterator(region, alignmentOptions, filters, clazz)
                : iterator(alignmentOptions, filters, clazz);

        while (alignmentIterator.hasNext() && results.size() < maxNumberRecords) {
            results.add(alignmentIterator.next());
        }
        alignmentIterator.close();
        return results;
    }


    public AlignmentIterator<SAMRecord> iterator() {
        return iterator(new AlignmentOptions(), null, SAMRecord.class);
    }

    public AlignmentIterator<SAMRecord> iterator(AlignmentOptions options) {
        return iterator(options, null, SAMRecord.class);
    }

    public AlignmentIterator<SAMRecord> iterator(AlignmentOptions options, AlignmentFilters filters) {
        return iterator(options, filters, SAMRecord.class);
    }

    public <T> AlignmentIterator<T> iterator(AlignmentOptions alignmentOptions, AlignmentFilters filters, Class<T> clazz) {
        if (alignmentOptions == null) {
            alignmentOptions = new AlignmentOptions();
        }
        SAMRecordIterator samRecordIterator = samReader.iterator();
        return getAlignmentIterator(filters, alignmentOptions.isBinQualities(), clazz, samRecordIterator);
    }

    public AlignmentIterator<SAMRecord> iterator(Region region) {
        return iterator(region, new AlignmentOptions(), null, SAMRecord.class);
    }

    public AlignmentIterator<SAMRecord> iterator(Region region, AlignmentOptions options) {
        return iterator(region, options, null, SAMRecord.class);
    }

    public AlignmentIterator<SAMRecord> iterator(Region region, AlignmentOptions options, AlignmentFilters filters) {
        return iterator(region, options, filters, SAMRecord.class);
    }

    public <T> AlignmentIterator<T> iterator(Region region, AlignmentOptions alignmentOptions, AlignmentFilters filters, Class<T> clazz) {
        if (alignmentOptions == null) {
            alignmentOptions = new AlignmentOptions();
        }
        SAMRecordIterator samRecordIterator =
                samReader.query(region.getChromosome(), region.getStart(), region.getEnd(), alignmentOptions.isContained());
        return getAlignmentIterator(filters, alignmentOptions.isBinQualities(), clazz, samRecordIterator);
    }

    public AlignmentGlobalStats stats() {
        AlignmentGlobalStats alignmentGlobalStats = new AlignmentGlobalStats();
        SamRecordAlignmentGlobalStatsCalculator calculator = new SamRecordAlignmentGlobalStatsCalculator();
        try(AlignmentIterator<SAMRecord> iterator = iterator()) {
            while(iterator.hasNext()) {
                AlignmentGlobalStats computed = calculator.compute(iterator.next());
                calculator.update(computed, alignmentGlobalStats);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return alignmentGlobalStats;
    }

    public AlignmentGlobalStats stats(Region region, AlignmentOptions options, AlignmentFilters filters) {
        AlignmentGlobalStats alignmentGlobalStats = new AlignmentGlobalStats();
        SamRecordAlignmentGlobalStatsCalculator calculator = new SamRecordAlignmentGlobalStatsCalculator();
        try(AlignmentIterator<SAMRecord> iterator = iterator(region, options, filters)) {
            while(iterator.hasNext()) {
                AlignmentGlobalStats computed = calculator.compute(iterator.next());
                calculator.update(computed, alignmentGlobalStats);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return alignmentGlobalStats;
    }

    public RegionCoverage coverage(Region region, AlignmentOptions options, AlignmentFilters filters) {
        int size = region.getEnd() - region.getStart() + 1;
        RegionCoverage regionCoverage = new RegionCoverage(region);
        SamRecordRegionCoverageCalculator calculator = new SamRecordRegionCoverageCalculator();
        try(AlignmentIterator<SAMRecord> iterator = iterator(region, options, filters)) {
            while(iterator.hasNext()) {
                RegionCoverage computed = calculator.compute(iterator.next());
                calculator.update(computed, regionCoverage);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return regionCoverage;
    }

    private <T> AlignmentIterator<T> getAlignmentIterator(AlignmentFilters filters, boolean binQualities, Class<T> clazz,
                                                          SAMRecordIterator samRecordIterator) {
        if (ReadAlignment.class == clazz) { // AVRO
            return (AlignmentIterator<T>) new AvroIterator(samRecordIterator, filters, binQualities);
        } else if (Reads.ReadAlignment.class == clazz) { // PROTOCOL BUFFER
            return (AlignmentIterator<T>) new ProtoIterator(samRecordIterator, filters, binQualities);
        } else if (SAMRecord.class == clazz) {
            return (AlignmentIterator<T>) new SamRecordIterator(samRecordIterator, filters);
        } else {
            throw new IllegalArgumentException("Unknown alignment class " + clazz);
        }
    }
}
