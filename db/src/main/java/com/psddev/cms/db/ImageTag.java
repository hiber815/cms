package com.psddev.cms.db;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.DynamicAttributes;
import javax.servlet.jsp.tagext.TagSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.psddev.dari.db.ObjectField;
import com.psddev.dari.db.ObjectType;
import com.psddev.dari.db.Recordable;
import com.psddev.dari.db.State;
import com.psddev.dari.util.ImageEditor;
import com.psddev.dari.util.JspUtils;
import com.psddev.dari.util.ObjectUtils;
import com.psddev.dari.util.StorageItem;
import com.psddev.dari.util.StringUtils;
import com.psddev.dari.util.TypeReference;
import com.psddev.dari.util.WebPageContext;

/**
 * Equivalent to the HTML {@code img} tag where its {@code src} attribute
 * may be set to a URL or a StorageItem object.
 */
@SuppressWarnings("serial")
public class ImageTag extends TagSupport implements DynamicAttributes {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ImageTag.class);

    protected Builder tagBuilder = new Builder();

    /**
     * Sets the source object, which may be either a URL or a Dari
     * object.
     */
    public void setSrc(Object src) {
        WebPageContext wp = new WebPageContext(pageContext);

        if (src instanceof String
                || src instanceof URI
                || src instanceof URL) {

            String path = JspUtils.resolvePath(wp.getServletContext(), wp.getRequest(), src.toString());
            StorageItem pathItem;
            if (path.startsWith("/")) {
                pathItem = StorageItem.Static.createUrl(JspUtils.getAbsoluteUrl(wp.getRequest(), path));
            } else {
                pathItem = StorageItem.Static.createUrl(path);
            }
            tagBuilder.setItem(pathItem);

        } else if (src instanceof StorageItem) {
            tagBuilder.setItem((StorageItem) src);

        } else if (src instanceof State || src instanceof Recordable) {

            // -- Hack to ensure backwards compatibility
            String field = (String) super.getValue("field");
            tagBuilder.setField(field);
            if (src instanceof State) {
                tagBuilder.setState((State) src);
            } else if (src instanceof Recordable) {
                tagBuilder.setRecordable((Recordable) src);
            }
            // -- End hack
        }
    }

    /**
     * Sets the field that contains the image. If not set, the first
     * field with {@value ObjectField.FILE_TYPE} type is used.
     * @deprecated No replacement
     */
    @Deprecated
    public void setField(String field) {
        tagBuilder.setField(field);
    }

    /**
     * Sets the name of the {@linkplain ImageEditor image editor}
     * to use.
     */
    public void setEditor(Object object) {
        ImageEditor editor = null;
        if (object instanceof ImageEditor) {
            editor = (ImageEditor) object;
        } else if (object instanceof String) {
            editor = ImageEditor.Static.getInstance((String) object);
        } else {
            editor = ImageEditor.Static.getDefault();
        }
        tagBuilder.setEditor(editor);
    }

    /**
     * Sets the internal name of the {@linkplain StandardImageSize
     * image size} to use.
     */
    public void setSize(Object size) {
        if (size instanceof StandardImageSize) {
            tagBuilder.setStandardImageSize((StandardImageSize) size);
        } else if (size instanceof String) {
            tagBuilder.setStandardImageSize(getStandardImageSizeByName((String) size));
        }
    }

    /**
     * Sets the width. Note that this will override the width provided
     * by the image size set with {@link #setSize(String)}.
     */
    public void setWidth(String width) {
        if (width != null && width.endsWith("px")) {
            width = width.substring(0, width.length()-2);
        }
        tagBuilder.setWidth(ObjectUtils.to(Integer.class, width));
    }

    /**
     * Sets the height. Note that this will override the height provided
     * by the image size set with {@link #setSize(String)}.
     */
    public void setHeight(String height) {
        if (height != null && height.endsWith("px")) {
            height = height.substring(0, height.length()-2);
        }
        tagBuilder.setHeight(ObjectUtils.to(Integer.class, height));
    }

    /**
     * Sets the crop option. Note that this will override the crop option
     * provided by the image size set with {@link #setSize(String)}.
     */
    public void setCropOption(Object cropOptionObject) {
        CropOption cropOption = null;
        if (cropOptionObject instanceof CropOption) {
            cropOption = (CropOption) cropOptionObject;
        } else if (cropOptionObject instanceof String) {
            cropOption = CropOption.Static.fromImageEditorOption((String) cropOptionObject);
        }
        tagBuilder.setCropOption(cropOption);
    }

    /**
     * Sets the resize option. Note that this will override the resize option
     * provided by the image size set with {@link #setSize(String)}.
     */
    public void setResizeOption(Object resizeOptionObject) {
        ResizeOption resizeOption = null;
        if (resizeOptionObject instanceof ResizeOption) {
            resizeOption = (ResizeOption) resizeOptionObject;
        } else if (resizeOptionObject instanceof String) {
            resizeOption = ResizeOption.Static.fromImageEditorOption((String) resizeOptionObject);
        }
        tagBuilder.setResizeOption(resizeOption);
    }

    /**
     * Overrides the default attribute (src) used to place the image URL. This
     * is usually used in the conjunction with lazy loading scripts that copy
     * the image URL from this attribute to the "src" attribute at some point
     * after the page has loaded.
     */
    public void setSrcAttr(String srcAttr) {
        tagBuilder.setSrcAttribute(srcAttr);
    }

    /**
     * When set to {@code true}, suppresses the "width" and "height" attributes
     * from the final HTML output.
     */
    public void setHideDimensions(Object hideDimensions) {
        if (ObjectUtils.to(boolean.class, hideDimensions)) {
            tagBuilder.hideDimensions();
        }
    }

    // --- DynamicAttribute support ---

    @Override
    public void setDynamicAttribute(String uri, String localName, Object value) {
        tagBuilder.addAttribute(localName, value);
    }

    // --- TagSupport support ---

    @Override
    public int doStartTag() throws JspException {
        JspWriter writer = pageContext.getOut();
        try {
            writer.print(tagBuilder.toHtml());
        } catch (IOException e) {
            throw new JspException(e);
        } finally {
            tagBuilder.reset();
        }

        return SKIP_BODY;
    }

    private static String convertAttributesToHtml(Map<String, String> attributes) {
        StringBuilder builder = new StringBuilder();
        if (!attributes.isEmpty()) {
            builder.append("<img");
            for (Map.Entry<String, String> e : attributes.entrySet()) {
                String key = e.getKey();
                String value = e.getValue();
                if (!(ObjectUtils.isBlank(key) || ObjectUtils.isBlank(value))) {
                    builder.append(" ");
                    builder.append(StringUtils.escapeHtml(key));
                    builder.append("=\"");
                    builder.append(StringUtils.escapeHtml(value));
                    builder.append("\"");
                }
            }
            builder.append(">");
        }
        return builder.toString();
    }

    /**
     * Finds the dimension {@code name} ("width", or "height") for the given
     * StorageItem {@code item}.
     */
    private static Integer findDimension(StorageItem item, String name) {
        if (item == null) {
            return null;
        }
        Integer dimension = null;
        Map<String, Object> metadata = item.getMetadata();
        if (metadata != null) {
            dimension = ObjectUtils.to(Integer.class, metadata.get(name));
            if (dimension == null || dimension == 0) {
                dimension = null;
            }
        }
        return dimension;
    }

    /**
     * Finds the crop information for the StorageItem {@code item}.
     */
    private static Map<String, ImageCrop> findImageCrops(StorageItem item) {
        if (item == null) {
            return null;
        }
        Map<String, ImageCrop> crops = null;
        Map<String, Object> metadata = item.getMetadata();
        if (metadata != null) {
            crops = ObjectUtils.to(new TypeReference<Map<String, ImageCrop>>() { }, metadata.get("cms.crops"));
        }
        if (crops == null) {
            crops = new HashMap<String, ImageCrop>();
        }
        return crops;
    }

    /**
     * @deprecated No replacement
     */
    @Deprecated
    private static Map<String, String> getAttributes(WebPageContext wp,
            Object src,
            String field,
            ImageEditor editor,
            StandardImageSize standardSize,
            Integer width,
            Integer height,
            CropOption cropOption,
            ResizeOption resizeOption,
            String srcAttr,
            Map<String, String> dynamicAttributes) {

        Builder tagBuilder = new Builder();

        if (src instanceof String
                || src instanceof URI
                || src instanceof URL) {

            tagBuilder.setItem(StorageItem.Static.createUrl(
                    JspUtils.getEmbeddedAbsolutePath(
                            wp.getServletContext(),
                            wp.getRequest(),
                            src.toString())));

        } else if (src instanceof StorageItem) {
            tagBuilder.setItem((StorageItem) src);

        } else if (src instanceof State || src instanceof Recordable) {

            // -- Hack to ensure backwards compatibility
            tagBuilder.setField(field);
            if (src instanceof State) {
                tagBuilder.setState((State) src);
            } else if (src instanceof Recordable) {
                tagBuilder.setRecordable((Recordable) src);
            }
            // -- End hack
        }

        return tagBuilder.setEditor(editor)
                .setStandardImageSize(standardSize)
                .setWidth(width)
                .setHeight(height)
                .setCropOption(cropOption)
                .setResizeOption(resizeOption)
                .setSrcAttribute(srcAttr)
                .addAllAttributes(dynamicAttributes)
                .toAttributes();
    }

    /**
     * @deprecated No replacement
     */
    @Deprecated
    private static String findStorageItemField(State state) {
        String field = null;
        ObjectType objectType = state.getType();
        if (objectType != null) {
            for (ObjectField objectField : objectType.getFields()) {
                if (ObjectField.FILE_TYPE.equals(objectField.getInternalType())) {
                    field = objectField.getInternalName();
                    break;
                }
            }
        }
        return field;
    }

    /**
     * @deprecated No replacement
     */
    @Deprecated
    private static StorageItem findStorageItem(State state, String field) {
        StorageItem item = null;
        if (field != null) {
            Object fieldValue = state.get(field);
            if (fieldValue instanceof StorageItem) {
                item = (StorageItem) fieldValue;
            }
        }
        return item;
    }

    /**
     * @deprecated Use {@link #findImageCrops(StorageItem)} instead
     */
    @Deprecated
    private static Map<String, ImageCrop> findImageCrops(State state, String field) {
        Map<String, ImageCrop> crops = null;
        Object fieldValue = state.get(field);
        if (fieldValue instanceof StorageItem) {
            crops = findImageCrops((StorageItem) fieldValue);
        }
        if (crops == null || crops.isEmpty()) {
            crops = ObjectUtils.to(new TypeReference<Map<String, ImageCrop>>() { }, state.getValue(field + ".crops"));
        }
        if (crops == null) {
            crops = new HashMap<String, ImageCrop>();
        }
        return crops;
    }

    /**
     * Finds the dimension value with the given {@code field} and
     * {@code name} from the given {@code state}.
     * @deprecated Use {@link #findDimension(StorageItem, String)} instead.
     */
    @Deprecated
    private static Integer findDimension(State state, String field, String name) {
        Integer dimension = null;
        Object fieldValue = state.get(field);
        if (fieldValue instanceof StorageItem) {
            dimension = findDimension((StorageItem) fieldValue, name);
        }
        if (dimension == null || dimension == 0) {
            dimension = ObjectUtils.to(Integer.class, state.getValue(field + ".metadata/" + name));
            if (dimension == null || dimension == 0) {
                dimension = ObjectUtils.to(Integer.class, state.getValue(field + "." + name));
                if (dimension == null || dimension == 0) {
                    dimension = null;
                }
            }
        }
        return dimension;
    }

    private static StandardImageSize getStandardImageSizeByName(String size) {
        StandardImageSize standardImageSize = null;
        for (StandardImageSize standardSize : StandardImageSize.findAll()) {
            if (standardSize.getInternalName().equals(size)) {
                standardImageSize = standardSize;
                break;
            }
        }
        return standardImageSize;
    }

    /**
     * <p>Static utility class for building HTML 'img' tags and URLs edited by
     * an {@link ImageEditor}. This class is functionally equivalent to calling
     * the JSTL &lt;cms:img&gt; tag in your JSP code.  Example usage:</p>
     * <pre>
     * StorageItem myStorageItem;
     *
     * String imageTagHtml = new ImageTag.Builder(myStorageItem)
     *      .setWidth(300)
     *      .setHeight(200)
     *      .addAttribute("class", "thumbnail")
     *      .addAttribute("alt", "My image")
     *      .toHtml();
     * </pre>
     * You can also grab just the image URL instead of the entire HTML output
     * by calling:
     * <pre>
     * String imageUrl = new ImageTag.Builder(myStorageItem)
     *      .setWidth(300)
     *      .setHeight(200)
     *      .toUrl()
     * </pre>
     */
    public static final class Builder {

        private StorageItem item;
        @Deprecated
        private String field;
        private ImageEditor editor;

        private StandardImageSize standardImageSize;

        private Integer width;
        private Integer height;
        private CropOption cropOption;
        private ResizeOption resizeOption;

        private String srcAttribute;
        private boolean hideDimensions;

        private final Map<String, String> attributes = new LinkedHashMap<String, String>();

        // for backwards compatibility
        private State state;

        public Builder(StorageItem item) {
            this.item = item;
        }

        private Builder() {
        }

        public StorageItem getItem() {
            return this.item;
        }

        protected Builder setItem(StorageItem item) {
            this.item = item;
            return this;
        }

        /** Resets all fields back to null */
        private void reset() {
            item = null;
            field = null;
            editor = null;
            standardImageSize = null;
            width = null;
            height = null;
            cropOption = null;
            resizeOption = null;
            srcAttribute = null;
            hideDimensions = false;
            attributes.clear();

            state = null;
        }

        /**
         * Sets the field that contains the image. If not set, the first
         * field with {@value ObjectField.FILE_TYPE} type is used.
         * @deprecated No replacement
         */
        @Deprecated
        private Builder setField(String field) {
            this.field = field;
            return this;
        }

        /**
         * Sets the name of the {@linkplain ImageEditor image editor}
         * to use.
         */
        public Builder setEditor(ImageEditor editor) {
            this.editor = editor;
            return this;
        }

        /**
         * Sets the internal name of the {@linkplain StandardImageSize
         * image size} to use.
         */
        public Builder setStandardImageSize(StandardImageSize standardImageSize) {
            this.standardImageSize = standardImageSize;
            return this;
        }

        /**
         * Sets the width. Note that this will override the width provided
         * by the image size set with {@link #setSize(String)}.
         */
        public Builder setWidth(Integer width) {
            this.width = width;
            return this;
        }

        /**
         * Sets the height. Note that this will override the height provided
         * by the image size set with {@link #setSize(String)}.
         */
        public Builder setHeight(Integer height) {
            this.height = height;
            return this;
        }

        /**
         * Sets the crop option. Note that this will override the crop option
         * provided by the image size set with {@link #setSize(String)}.
         */
        public Builder setCropOption(CropOption cropOption) {
            this.cropOption = cropOption;
            return this;
        }

        /**
         * Sets the resize option. Note that this will override the resize option
         * provided by the image size set with {@link #setSize(String)}.
         */
        public Builder setResizeOption(ResizeOption resizeOption) {
            this.resizeOption = resizeOption;
            return this;
        }

        /**
         * Overrides the default attribute (src) used to place the image URL. This
         * is usually used in the conjunction with lazy loading scripts that copy
         * the image URL from this attribute to the "src" attribute at some point
         * after the page has loaded.
         */
        public Builder setSrcAttribute(String srcAttribute) {
            this.srcAttribute = srcAttribute;
            return this;
        }

        /**
         * Set to true if the resulting image dimensions should be removed
         * from the final tag output.
         */
        public Builder hideDimensions() {
            this.hideDimensions = true;
            return this;
        }

        /**
         * Adds an attribute to be placed on the tag.
         */
        public Builder addAttribute(String name, Object value) {
            this.attributes.put(name, value != null ? value.toString() : null);
            return this;
        }

        /**
         * Adds all the attributes to be placed on the tag.
         */
        public Builder addAllAttributes(Map<String, ?> attributes) {
            if (attributes != null) {
                for (Map.Entry<String, ?> entry : attributes.entrySet()) {
                    addAttribute(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        /**
         * For backwards compatibility
         *
         * @deprecated
         */
        @Deprecated
        private Builder setState(State state) {
            this.state = state;
            return this;
        }

        /**
         * For backwards compatibility
         *
         * @deprecated
         */
        @Deprecated
        private Builder setRecordable(Recordable recordable) {
            setState(State.getInstance(recordable));
            return this;
        }

        /**
         *
         * @return the HTML for an img tag constructed by this Builder.
         */
        public String toHtml() {
            return convertAttributesToHtml(toAttributes());
        }

        /**
         *
         * @return the URL to the image as a String.
         */
        public String toUrl() {
            return toAttributes().get(srcAttribute != null ? srcAttribute : "src");
        }

        /** Returns all the attributes that will get placed on the img tag. */
        private Map<String, String> toAttributes() {
            // set all the attributes
            Map<String, String> attributes = new LinkedHashMap<String, String>();

            ImageEditor editor = this.editor;

            StandardImageSize standardImageSize = this.standardImageSize;

            Integer width = this.width;
            Integer height = this.height;
            CropOption cropOption = this.cropOption;
            ResizeOption resizeOption = this.resizeOption;

            String srcAttr = this.srcAttribute;
            boolean hideDimensions = this.hideDimensions;

            StorageItem item = null;
            Integer originalWidth = null;
            Integer originalHeight = null;
            Map<String, ImageCrop> crops = null;

            if (this.state != null) { // backwards compatibility path
                State objectState = this.state;
                String field = this.field;

                if (ObjectUtils.isBlank(field)) {
                    field = findStorageItemField(objectState);
                }

                item = findStorageItem(objectState, field);

                if (item != null) {
                    originalWidth = findDimension(objectState, field, "width");
                    originalHeight = findDimension(objectState, field, "height");
                    crops = findImageCrops(objectState, field);
                }

            } else { // new code path
                item = this.item;

                if (item != null) {
                    originalWidth = findDimension(item, "width");
                    originalHeight = findDimension(item, "height");
                    crops = findImageCrops(item);
                }
            }

            // null out all dimensions that are less than or equal to zero
            originalWidth = originalWidth != null && originalWidth <= 0 ? null : originalWidth;
            originalHeight = originalHeight != null && originalHeight <= 0 ? null : originalHeight;
            width = width != null && width <= 0 ? null : width;
            height = height != null && height <= 0 ? null : height;

            if (item != null) {
                Map<String, Object> options = new LinkedHashMap<String, Object>();

                Integer cropX = null, cropY = null, cropWidth = null, cropHeight = null;

                // set fields from this standard size if they haven't already been set
                if (standardImageSize != null) {
                    // get the standard image dimensions
                    if (width == null) {
                        width = standardImageSize.getWidth();
                        if (width <= 0) {
                            width = null;
                        }
                    }
                    if (height == null) {
                        height = standardImageSize.getHeight();
                        if (height <= 0) {
                            height = null;
                        }
                    }

                    // get the crop and resize options
                    if (cropOption == null) {
                        cropOption = standardImageSize.getCropOption();
                    }
                    if (resizeOption == null) {
                        resizeOption = standardImageSize.getResizeOption();
                    }

                    // get the crop coordinates
                    ImageCrop crop;
                    if (crops != null && (crop = crops.get(standardImageSize.getId().toString())) != null &&
                            originalWidth != null && originalHeight != null) {

                        cropX = (int) (crop.getX() * originalWidth);
                        cropY = (int) (crop.getY() * originalHeight);
                        cropWidth = (int) (crop.getWidth() * originalWidth);
                        cropHeight = (int) (crop.getHeight() * originalHeight);
                    }
                }

                // if the crop info is unavailable, assume that the image
                // dimensions are the crop dimensions in case the image editor
                // knows how to crop without the x & y coordinates
                if (cropWidth == null) {
                    cropWidth = width;
                }
                if (cropHeight == null) {
                    cropHeight = height;
                }

                // set the options
                if (cropOption != null) {
                    options.put(ImageEditor.CROP_OPTION, cropOption.getImageEditorOption());
                }
                if (resizeOption != null) {
                    options.put(ImageEditor.RESIZE_OPTION, resizeOption.getImageEditorOption());
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> edits = (Map<String, Object>) item.getMetadata().get("cms.edits");

                // Requires at least the width and height to perform a crop
                if (cropWidth != null && cropHeight != null) {
                    item = ImageEditor.Static.crop(editor, item, options, cropX, cropY, cropWidth, cropHeight);
                }

                // Requires only one of either the width or the height to perform a resize
                if (width != null || height != null) {
                    item = ImageEditor.Static.resize(editor, item, options, width, height);
                }

                if (edits != null) {
                    ImageEditor realEditor = editor;
                    if (realEditor == null) {
                        realEditor = ImageEditor.Static.getDefault();
                    }
                    for (Map.Entry<String, Object> entry : new TreeMap<String, Object>(edits).entrySet()) {
                        item = realEditor.edit(item, entry.getKey(), null, entry.getValue());
                    }
                }

                String url = item.getPublicUrl();
                if (url != null) {
                    attributes.put(srcAttr != null ? srcAttr : "src", url);
                }

                Integer newWidth = findDimension(item, "width");
                Integer newHeight = findDimension(item, "height");
                if (newWidth != null && !hideDimensions) {
                    attributes.put("width", String.valueOf(newWidth));
                }
                if (newHeight != null && !hideDimensions) {
                    attributes.put("height", String.valueOf(newHeight));
                }

                if (this.attributes != null) {
                    attributes.putAll(this.attributes);
                }
            }

            return attributes;
        }
    }

    public final static class Static {

        private Static() {
        }

        /**
         * @deprecated Use {@link ImageTag.Builder} instead.
         */
        @Deprecated
        public static String getHtmlFromStandardImageSize(WebPageContext wp,
                StorageItem item,
                ImageEditor editor,
                StandardImageSize size,
                String srcAttr,
                Map<String, String> dynamicAttributes) {

            return getHtmlFromOptions(wp,
                    item,
                    editor,
                    size.getWidth(),
                    size.getHeight(),
                    size.getCropOption(),
                    size.getResizeOption(),
                    srcAttr,
                    dynamicAttributes);
        }

        /**
         * @deprecated Use {@link ImageTag.Builder} instead.
         */
        @Deprecated
        public static String getHtmlFromOptions(WebPageContext wp,
                StorageItem item,
                ImageEditor editor,
                Integer width,
                Integer height,
                CropOption cropOption,
                ResizeOption resizeOption,
                String srcAttr,
                Map<String, String> dynamicAttributes) {

            return new Builder(item)
                .setEditor(editor)
                .setWidth(width)
                .setHeight(height)
                .setCropOption(cropOption)
                .setResizeOption(resizeOption)
                .setSrcAttribute(srcAttr)
                .addAllAttributes(dynamicAttributes)
                .toHtml();
        }

        /**
         * @deprecated Use {@link ImageTag.Builder} instead.
         */
        @Deprecated
        public static String getUrlFromStandardImageSize(WebPageContext wp,
                StorageItem item,
                ImageEditor editor,
                StandardImageSize size) {

            return getUrlFromOptions(wp,
                    item,
                    editor,
                    size.getWidth(),
                    size.getHeight(),
                    size.getCropOption(),
                    size.getResizeOption());
        }

        /**
         * @deprecated Use {@link ImageTag.Builder} instead.
         */
        @Deprecated
        public static String getUrlFromOptions(WebPageContext wp,
                StorageItem item,
                ImageEditor editor,
                Integer width,
                Integer height,
                CropOption cropOption,
                ResizeOption resizeOption) {

            return new Builder(item)
                .setEditor(editor)
                .setWidth(width)
                .setHeight(height)
                .setCropOption(cropOption)
                .setResizeOption(resizeOption)
                .toUrl();
        }

        /**
         * @deprecated Use {@link ImageTag.Builder} instead.
         */
        @Deprecated
        public static String getHtml(WebPageContext wp,
                Object object,
                String field,
                ImageEditor editor,
                StandardImageSize standardSize,
                Integer width,
                Integer height,
                CropOption cropOption,
                ResizeOption resizeOption,
                String srcAttr,
                Map<String, String> dynamicAttributes) {

            Map<String, String> attributes = getAttributes(wp,
                    object, field, editor, standardSize, width, height, cropOption, resizeOption, srcAttr, dynamicAttributes);

            return convertAttributesToHtml(attributes);
        }

        /**
         * @deprecated Use {@link ImageTag.Builder} instead.
         */
        @Deprecated
        public static String getHtml(PageContext pageContext,
                Object object,
                String field,
                ImageEditor editor,
                StandardImageSize standardSize,
                Integer width,
                Integer height,
                CropOption cropOption,
                ResizeOption resizeOption,
                String srcAttr,
                Map<String, String> dynamicAttributes) {
            return getHtml(new WebPageContext(pageContext),
                    object, field, editor, standardSize, width, height, cropOption, resizeOption, srcAttr, dynamicAttributes);
        }

        /**
         * @deprecated Use {@link ImageTag.Builder} instead.
         */
        @Deprecated
        public static String makeUrlFromStandardImageSize(WebPageContext wp,
                Object object,
                String field,
                ImageEditor editor,
                String size) {

            StandardImageSize standardImageSize = getStandardImageSizeByName(size);

            Map<String, String> attributes = getAttributes(wp,
                    object, field, editor, standardImageSize, null, null, null, null, null, null);
            return attributes.get("src");
        }

        /**
         * @deprecated Use {@link ImageTag.Builder} instead.
         */
        @Deprecated
        public static String makeUrlFromStandardImageSize(PageContext pageContext,
                Object object,
                String field,
                ImageEditor editor,
                String size) {

            return makeUrlFromStandardImageSize(new WebPageContext(pageContext), object, field, editor, size);
        }

        /**
         * @deprecated Use {@link ImageTag.Builder} instead.
         */
        @Deprecated
        public static String makeUrlFromOptions(WebPageContext wp,
                Object object,
                String field,
                ImageEditor editor,
                Integer width,
                Integer height,
                CropOption cropOption,
                ResizeOption resizeOption) {

            Map<String, String> attributes = getAttributes(wp,
                    object, field, editor, null, width, height, cropOption, resizeOption, null, null);
            return attributes.get("src");
        }

        /**
         * @deprecated Use {@link ImageTag.Builder} instead.
         */
        @Deprecated
        public static String makeUrlFromOptions(PageContext pageContext,
                Object object,
                String field,
                ImageEditor editor,
                Integer width,
                Integer height,
                CropOption cropOption,
                ResizeOption resizeOption) {
            return makeUrlFromOptions(new WebPageContext(pageContext), object, field, editor, width, height, cropOption, resizeOption);
        }

    }

    /**
     * @deprecated Use {@link ImageTag.Builder} instead.
     */
    @Deprecated
    public static String makeUrl(
            PageContext pageContext,
            Object object,
            String field,
            String editor,
            String size,
            Integer width,
            Integer height) {

        StandardImageSize standardImageSize = getStandardImageSizeByName(size);

        ImageEditor imageEditor = null;
        if (editor != null) {
            imageEditor = ImageEditor.Static.getInstance(editor);
        }
        if (imageEditor == null) {
            imageEditor = ImageEditor.Static.getDefault();
        }

        Map<String, String> attributes = getAttributes(new WebPageContext(pageContext),
                object, field, imageEditor, standardImageSize, width, height, null, null, null, null);
        return attributes.get("src");
    }
}
