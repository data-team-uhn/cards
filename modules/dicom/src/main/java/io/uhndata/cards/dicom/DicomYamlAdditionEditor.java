/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.dicom;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.ArrayBasedBlob;
import org.apache.jackrabbit.oak.spi.commit.DefaultEditor;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Keyword;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DicomYamlAdditionEditor extends DefaultEditor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomYamlAdditionEditor.class);

    // This holds the builder for the current node. The methods called for editing specific properties don't receive the
    // actual parent node of those properties, so we must manually keep track of the current node.
    private final NodeBuilder currentNodeBuilder;

    // This holds a list of NodeBuilders with the first item corresponding to the root of the JCR tree
    // and the last item corresponding to the current node. By keeping this list, one is capable of
    // moving up the tree and setting status flags of ancestor nodes based on the status flags of a
    // descendant node.
    private final List<NodeBuilder> currentNodeBuilderPath;

    private boolean currentNodeBuilderIsDicom;

    /**
     * Simple constructor.
     *
     * @param nodeBuilderPath the list of builders up to and including the current node
     */
    public DicomYamlAdditionEditor(List<NodeBuilder> nodeBuilderPath)
    {
        this.currentNodeBuilderPath = nodeBuilderPath;
        this.currentNodeBuilder = nodeBuilderPath.get(nodeBuilderPath.size() - 1);
        this.currentNodeBuilderIsDicom = false;
    }

    // Called when a new property is added
    @Override
    public void propertyAdded(PropertyState after) throws CommitFailedException
    {
        handlePropertyAdded(after);
    }

    // Called when the value of an existing property gets changed
    @Override
    public void propertyChanged(PropertyState before, PropertyState after) throws CommitFailedException
    {
        handlePropertyAdded(after);
    }

    // When something changes in a node deep in the content tree, the editor is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultEditor is to stop at the root, so we must override the following two methods in order for the editor to be
    // invoked on non-root nodes.
    @Override
    public Editor childNodeAdded(String name, NodeState after) throws CommitFailedException
    {
        final List<NodeBuilder> tmpList = new ArrayList<>(this.currentNodeBuilderPath);
        tmpList.add(this.currentNodeBuilder.getChildNode(name));
        return new DicomYamlAdditionEditor(tmpList);
    }

    @Override
    public Editor childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        final List<NodeBuilder> tmpList = new ArrayList<>(this.currentNodeBuilderPath);
        tmpList.add(this.currentNodeBuilder.getChildNode(name));
        return new DicomYamlAdditionEditor(tmpList);
    }

    @SuppressWarnings("checkstyle:ExecutableStatementCount")
    @Override
    public void leave(NodeState before, NodeState after)
    {
        if (this.currentNodeBuilderIsDicom) {
            //LOGGER.warn("This is for a DICOM JCR node...{}", this.currentNodeBuilderPath);
            // Implement DICOM parsing
            String dicomMetadataString = "";
            try {
                //LOGGER.warn("Trying to parse the DICOM metadata...");
                InputStream uploadedDicomStream = after.getProperty("jcr:data").getValue(Type.BINARY).getNewStream();
                DicomInputStream dis = new DicomInputStream(uploadedDicomStream);
                Attributes attributes = dis.readDataset();
                int[] tags = attributes.tags();
                int imageHeight = 0;
                int imageWidth = 0;
                int bitsPerPixel = 0;
                byte[] pixelData = null;
                for (int tag : tags) {
                    String tagAddress = TagUtils.toString(tag);
                    String tagName = Keyword.valueOf(tag);
                    String tagValue = attributes.getString(tag);
                    if ("PixelData".equals(tagName)) {
                        LOGGER.warn("Found the PixelData attribute!");
                        //byte[] pixelData = attributes.getBytes(tag);
                        pixelData = attributes.getBytes(tag);
                        //LOGGER.warn("Read {} bytes of DICOM pixel data", pixelData.length);
                    }
                    if ("Rows".equals(tagName)) {
                        LOGGER.warn("Found the Rows attribute!");
                        imageHeight = attributes.getInt(tag, 0);
                    }
                    if ("Columns".equals(tagName)) {
                        LOGGER.warn("Found the Columns attribute!");
                        imageWidth = attributes.getInt(tag, 0);
                    }
                    if ("BitsAllocated".equals(tagName)) {
                        LOGGER.warn("Found the BitsAllocated attribute!");
                        bitsPerPixel = attributes.getInt(tag, 0);
                    }
                    //LOGGER.warn("TAG ADDRESS = {}, TAG NAME = {}, TAG VALUE = {}", tagAddress, tagName, tagValue);
                    if (dicomMetadataString.length() > 0) {
                        dicomMetadataString += ", ";
                    }
                    dicomMetadataString += tagName + ": " + tagValue;
                }
                LOGGER.warn("Found an image with - height={}, width={}, bitsPerPixel={}, pixelDataLength={}",
                    imageHeight,
                    imageWidth,
                    bitsPerPixel,
                    pixelData.length
                );
                dis.close();
                uploadedDicomStream.close();

                BufferedImage img = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_USHORT_GRAY);
                for (int i = 0; i < pixelData.length; i += 2) {
                    short thisPixel = (short) (((pixelData[i] & 255) << 8) | (pixelData[i + 1] & 255));
                    //int[] tmp = {((pixelData[i] << 8) | pixelData[i + 1]) / 65535};
                    int[] tmp = {thisPixel};
                    //tmp[0] = tmp[0] + 32768;
                    img.getRaster().setPixel((i / 2) % imageWidth, (i / 2) / imageHeight, tmp);
                }
                ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
                ImageIO.write(img, "png", pngOutputStream);
                byte[] pngBytes = pngOutputStream.toByteArray();
                ArrayBasedBlob pngBlob = new ArrayBasedBlob(pngBytes);
                NodeBuilder dicomAnswerNodeBuilder = this.currentNodeBuilderPath.get(
                    this.currentNodeBuilderPath.size() - 3);
                dicomAnswerNodeBuilder.setProperty("image", pngBlob);
            } catch (IOException e) {
                LOGGER.warn("Failed to parse the DICOM metadata...");
            }
            NodeBuilder dicomAnswerNodeBuilder = this.currentNodeBuilderPath.get(
                this.currentNodeBuilderPath.size() - 3);
            //LOGGER.warn("dicomAnswerNodeBuilder = {}", dicomAnswerNodeBuilder);
            dicomAnswerNodeBuilder.setProperty("note", dicomMetadataString);
        }
    }

    private void handlePropertyAdded(PropertyState state) throws CommitFailedException
    {
        if ("jcr:mimeType".equals(state.getName())) {
            if ("application/dicom".equals(state.getValue(Type.STRING))) {
                //LOGGER.warn("A DICOM file was just uploaded to {}", this.currentNodeBuilder);
                this.currentNodeBuilderIsDicom = true;
            }
        }
    }
}
