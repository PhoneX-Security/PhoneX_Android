package net.phonex.ui.customViews;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import net.phonex.util.Log;

/**
 * FlowLayout container, causing subviews to wrap the line.
 * Created by dusanklinec on 25.04.15.
 */
public class FlowLayout extends ViewGroup {

    /**
     * Internal variable, maximal line height with current container content (max through all children).
     */
    private int line_height;
    private int rowCount = -1;
    private int colCount = -1;

    /**
     * If set to yes, no additional child stretching is performed. E.g., if cols=2, rows=2, # of elems=3
     * then with widthFixedToCols=false, the last element has full width, with widthFixedToCols=true the last
     * element has same widh as element above preserving grid like layout.
     */
    private boolean widthFixedToCols = false;

    /**
     * If true, children width will be stretched so that they use maximal possible free space in the layout.
     * MinimalChildWidth is important in computation number of cols.
     */
    private boolean stretchChildren = false;

    /**
     * If yes, number of columns will be adjusted so elements optimize free space usage with fixed number of
     * rows. If rows = 2, and # of elements = 8, then default layout 7,1 (objects on a row) will be recomputed to 4,4 objects on a row.
     */
    private boolean recomputeColsToEven = false;

    /**
     * Minimal desired width of the child element. Taken into account when stretchChildren == true,
     * then number of cols is derived using this width, same for all elements in the container.
     */
    private int minimalChildWidth = -1;
    private int lastMeasuredHeight = -1;

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public final int horizontal_spacing;
        public final int vertical_spacing;

        /**
         * @param horizontal_spacing Pixels between items, horizontally
         * @param vertical_spacing Pixels between items, vertically
         */
        public LayoutParams(int horizontal_spacing, int vertical_spacing) {
            super(0, 0);
            this.horizontal_spacing = horizontal_spacing;
            this.vertical_spacing = vertical_spacing;
        }
    }

    public FlowLayout(Context context) {
        super(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(1, 1); // default of 1px spacing
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    /**
     * Measure child objects and compute size of this object.
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final boolean doStretch = stretchChildren && minimalChildWidth > 0;
        if (!doStretch){
            defaultOnMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        final int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        final int count = getChildCount();
        int height = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();
        int line_height = 0;
        int xpos = getPaddingLeft();
        int ypos = getPaddingTop();
        int curMaxCols = -1;
        int curColElems = 0;
        int totalVisibleChild = 0;

        int childHeightMeasureSpec;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
        } else {
            childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        }

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            final int childWidth = minimalChildWidth;
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.AT_MOST), childHeightMeasureSpec);
//            Log.ef("FLOW", "--measure, childWidth: %d, minimal: %d, childHeight: %d, lpWidth: %d, lpHeight: %d",
//                    child.getMeasuredWidth(), minimalChildWidth, child.getMeasuredHeight(),
//                    lp.width, lp.height);

//            final int childWidth = Math.min(minimalChildWidth, child.getMeasuredWidth()); // TODO: fix real height.
            line_height = Math.max(line_height, child.getMeasuredHeight() + lp.vertical_spacing);

            // Wrapping is done here.
            if ((xpos + childWidth) > width) {
                curMaxCols = Math.max(curColElems, curMaxCols);
                xpos = getPaddingLeft();
                ypos += line_height;
                curColElems = 0;
            }

            curColElems += 1;
            totalVisibleChild += 1;
            xpos += childWidth + lp.horizontal_spacing;
        }

        this.line_height = line_height;
        this.colCount = Math.max(curColElems, curMaxCols);
        this.rowCount = Math.max(1, (int) Math.ceil(totalVisibleChild / (double)colCount));

        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            height = this.rowCount * this.line_height; // ypos + line_height;

        } else if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            if (this.rowCount * this.line_height < height) {
                height = this.rowCount * this.line_height;
            }
        }

        lastMeasuredHeight = this.rowCount * this.line_height;

        // Balance scenarios with 7 elements in one row and 1 in the next one.
        // Try to optimize free space utilization with child elements.
        if (recomputeColsToEven){
            this.colCount = Math.min(this.colCount, (int) Math.ceil(totalVisibleChild / (double) rowCount));
        }

//        Log.ef("FLOW", "measured, width: %d, height: %d, cols: %d, rows: %d, minimal: %d", width, height, colCount, rowCount, minimalChildWidth);
        setMeasuredDimension(width, height);
    }

    /**
     * Tries to estimate height of the object using only static statistics, no container objects.
     * @return
     */
    public int estimateHeight(int width, int verticalSpacing, int numOfElements, int minimalChildHeight,
                              int numOfSmallElements, int minimalSmallChildHeight, int smallFilesAgregateCount){
        if (numOfElements != 0 && numOfSmallElements != 0) {
            // put small elements in a group of smallFilesAgregateCount, then treat as a big element
            // number of groups of small elements

            // if there is mix of small and big elements, small elements will be grouped, to decrease used space
            // however if there is e.g. 1 small element, it will be in group by itself with some empty space
            // to fix this, FlowLayout needs to be modified further, not just the height estimate

            int numOfAggregates = (numOfSmallElements + smallFilesAgregateCount - 1) / smallFilesAgregateCount;
            numOfElements += numOfAggregates;
        } else if (numOfElements == 0) {
            // only small elements
            numOfElements = numOfSmallElements;
            minimalChildHeight = minimalSmallChildHeight;
        } // else - only big elements

        final int cols = Math.max(1, (int) Math.floor(width / (double)minimalChildWidth));
        final int rows = Math.max(1, (int) Math.ceil(numOfElements / (double) cols));
        int result = getPaddingTop() + getPaddingBottom();
        if (rows > 1){
            result += (rows - 1) * (minimalChildHeight + verticalSpacing) + minimalChildHeight;
        } else {
            result += rows * minimalChildHeight;
        }

        return result;
    }

    /**
     * Position all children within this layout.
     *
     * @param changed
     * @param l
     * @param t
     * @param r
     * @param b
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final boolean doStrech = stretchChildren && colCount > 0 && minimalChildWidth > 0;
        if (!doStrech){
            defaultLayout(changed, l, t, r, b);
            return;
        }

        final int count = getChildCount();
        int curColFill = 0;
        int curRow = 0;

        // Collect all views in a column here.
        final View[] subCols = new View[colCount];
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            subCols[curColFill] = child;
            curColFill += 1;

            if (curColFill >= colCount){
                colFinished(subCols, curColFill, curRow, l, t, r, b);
                curColFill = 0;
                curRow += 1;
            }
        }

        // Last row.
        if (curColFill > 0) {
            colFinished(subCols, curColFill, curRow, l, t, r, b);
            curColFill = 0;
        }
    }

    /**
     * Handles positioning children inside one row. Manages children stretching.
     * @param subView
     * @param cn
     * @param row
     * @param l
     * @param t
     * @param r
     * @param b
     */
    private void colFinished(View subView[], int cn, int row, int l, int t, int r, int b){
        final int width  = r - l - getPaddingLeft() - getPaddingRight();
        int xpos = getPaddingLeft();
        int ypos = getPaddingTop() + row * line_height;

        // Compute total horizontal spacing.
        int totalHspace = 0;
        for (int i = 0; i < cn - 1; i++) {
            final View child = subView[i];
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            totalHspace += lp.horizontal_spacing;
        }

        final int elemWidth = widthFixedToCols ? (width - totalHspace) / colCount : (width - totalHspace) / cn;
//        Log.ef("FLOW", "subView, curRow=%d, cnInCol=%d, width: %d, hspace: %d, elemWidth: %d, xpos: %d, ypos: %d, lineHeight: %d",
//                row, cn, width, totalHspace, elemWidth, xpos, ypos, line_height);

        // Position children inside layout.
        for (int i = 0; i < cn; i++) {
            final View child = subView[i];
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final int childHeight = child.getMeasuredHeight();

            // Re-measure child so it adjusts its layout for optimal values.
            child.measure(MeasureSpec.makeMeasureSpec(elemWidth, MeasureSpec.EXACTLY),
                          MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY));

            // Set drawing bounding box of the child element.
            child.layout(xpos, ypos, xpos + elemWidth, ypos + childHeight);
//            Log.ef("FLOW", "--child.layout: l: %d, t: %d, r: %d, b: %d", xpos, ypos, xpos + elemWidth, ypos + childHeight);

            // Offset child x position by child width + space parameter.
            xpos += elemWidth + lp.horizontal_spacing;
        }
    }

    /**
     * Default flow layout onLayout handler, without children stretching.
     * @param changed
     * @param l
     * @param t
     * @param r
     * @param b
     */
    private void defaultLayout(boolean changed, int l, int t, int r, int b){
        final int count = getChildCount();
        final int width = r - l;
        int xpos = getPaddingLeft();
        int ypos = getPaddingTop();

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE){
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final int childHeight = child.getMeasuredHeight();
            final int childWidth  = child.getMeasuredWidth();

            // Wrapping happens here, new line.
            if (xpos + childWidth > width) {
                xpos = getPaddingLeft();
                ypos += line_height;
            }

            child.layout(xpos, ypos, xpos + childWidth, ypos + childHeight);

            // Offset child x position by child width + space parameter.
            xpos += childWidth + lp.horizontal_spacing;
        }
    }

    /**
     * Default on measure when we dont require children stretching.
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    private void defaultOnMeasure(int widthMeasureSpec, int heightMeasureSpec){
        final boolean doStretch = stretchChildren && minimalChildWidth > 0;
        final int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        final int count = getChildCount();
        int height = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();
        int line_height = 0;
        int xpos = getPaddingLeft();
        int ypos = getPaddingTop();

        int childHeightMeasureSpec;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
        } else {
            childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        }

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), childHeightMeasureSpec);
            final int childWidth = child.getMeasuredWidth();
            line_height = Math.max(line_height, child.getMeasuredHeight() + lp.vertical_spacing);

            // Wrapping is done here.
            if (xpos + childWidth > width) {
                xpos = getPaddingLeft();
                ypos += line_height;
            }

            xpos += childWidth + lp.horizontal_spacing;
        }

        this.line_height = line_height;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            height = ypos + line_height;

        } else if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            if (ypos + line_height < height) {
                height = ypos + line_height;
            }
        }

        setMeasuredDimension(width, height);
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public int getColCount() {
        return colCount;
    }

    public void setColumnCount(int colCount) {
        this.colCount = colCount;
    }

    public boolean isStretchChildren() {
        return stretchChildren;
    }

    /**
     * If true, children width will be stretched so that they use maximal possible free space in the layout.
     * MinimalChildWidth is important in computation number of cols.
     * @param stretchChildren
     */
    public void setStretchChildren(boolean stretchChildren) {
        this.stretchChildren = stretchChildren;
    }

    public int getMinimalChildWidth() {
        return minimalChildWidth;
    }

    /**
     * Minimal desired width of the child element. Taken into account when stretchChildren == true,
     * then number of cols is derived using this width, same for all elements in the container.
     *
     * @param minimalChildWidth
     */
    public void setMinimalChildWidth(int minimalChildWidth) {
        this.minimalChildWidth = minimalChildWidth;
    }

    public boolean isWidthFixedToCols() {
        return widthFixedToCols;
    }

    /**
     * If set to yes, no additional child stretching is performed. E.g., if cols=2, rows=2, # of elems=3
     * then with widthFixedToCols=false, the last element has full width, with widthFixedToCols=true the last
     * element has same width as element above preserving grid like layout.
     *
     * @param widthFixedToCols
     */
    public void setWidthFixedToCols(boolean widthFixedToCols) {
        this.widthFixedToCols = widthFixedToCols;
    }

    public boolean isRecomputeColsToEven() {
        return recomputeColsToEven;
    }

    /**
     * If yes, number of columns will be adjusted so elements optimize free space usage with fixed number of
     * rows. If rows = 2, and # of elements = 8, then default layout 7,1 (objects on a row) will be recomputed to 4,4 objects on a row.
     *
     * @param recomputeColsToEven
     */
    public void setRecomputeColsToEven(boolean recomputeColsToEven) {
        this.recomputeColsToEven = recomputeColsToEven;
    }

    public int getLastMeasuredHeight() {
        return lastMeasuredHeight;
    }
}
