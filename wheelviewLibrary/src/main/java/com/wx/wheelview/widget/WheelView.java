/*
 * Copyright (C) 2016 venshine.cn@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wx.wheelview.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.FloatRange;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.wx.wheelview.adapter.BaseWheelAdapter;
import com.wx.wheelview.common.WheelConstants;
import com.wx.wheelview.common.WheelViewException;
import com.wx.wheelview.util.WheelUtils;

import java.util.HashMap;
import java.util.List;

/**
 * 滚轮控件
 *
 * @author venshine
 */
public class WheelView<T> extends ListView implements IWheelView<T> {

    private int mItemH = 0; // 每一项高度
    private int mWheelSize = WHEEL_SIZE;    // 滚轮个数
    private boolean mLoop = LOOP;   // 是否循环滚动
    private List<T> mList = null;   // 滚轮数据列表
    private int mCurrentPositon = -1;    // 记录滚轮当前刻度
    private String mExtraText;  // 添加滚轮选中位置附加文本
    private int mExtraTextColor;    // 附加文本颜色
    private int mExtraTextSize; // 附加文本大小
    private int mExtraMargin;   // 附加文本外边距
    private int mSelection = 0; // 选中位置
    private boolean mClickable = CLICKABLE; // 是否可点击

    private Paint mTextPaint;   // 附加文本画笔


    private WheelViewStyle mStyle;  // 滚轮样式

    private WheelView mJoinWheelView;   // 副WheelView

    private HashMap<String, List<T>> mJoinMap;    // 副滚轮数据列表

    private BaseWheelAdapter<T> mWheelAdapter;

    private OnWheelItemSelectedListener<T> mOnWheelItemSelectedListener;

    private OnWheelItemClickListener<T> mOnWheelItemClickListener;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == WheelConstants.WHEEL_SCROLL_HANDLER_WHAT) {
                if (mOnWheelItemSelectedListener != null) {
                    mOnWheelItemSelectedListener.onItemSelected
                            (getCurrentPosition(), getSelectionItem());
                }
                if (mJoinWheelView != null) {
                    if (!mJoinMap.isEmpty()) {
                        mJoinWheelView.resetDataFromTop(mJoinMap.get(mList.get
                                (getCurrentPosition()))
                        );
                    } else {
                        throw new WheelViewException("JoinList is error.");
                    }
                }
            }
        }
    };

    private OnItemClickListener mOnItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (mOnWheelItemClickListener != null) {
                mOnWheelItemClickListener.onItemClick(getCurrentPosition(), getSelectionItem());
            }
        }
    };

    private OnTouchListener mTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        }
    };

    private OnScrollListener mOnScrollListener = new OnScrollListener() {
        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            if (scrollState == SCROLL_STATE_IDLE) {
                View itemView = getChildAt(0);
                if (itemView != null) {
                    float deltaY = itemView.getY();
                    if (deltaY == 0 || mItemH == 0) {
                        return;
                    }
                    if (Math.abs(deltaY) < mItemH / 2) {
                        int d = getSmoothDistance(deltaY);
                        smoothScrollBy(d, WheelConstants
                                .WHEEL_SMOOTH_SCROLL_DURATION);
                    } else {
                        int d = getSmoothDistance(mItemH + deltaY);
                        smoothScrollBy(d, WheelConstants
                                .WHEEL_SMOOTH_SCROLL_DURATION);
                    }
                }
            }
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int
                visibleItemCount, int totalItemCount) {
            if (visibleItemCount != 0) {
                refreshCurrentPosition(false);
            }
        }
    };

    public WheelView(Context context) {
        super(context);
        init();
    }

    public WheelView(Context context, WheelViewStyle style) {
        super(context);
        setStyle(style);
        init();
    }

    public WheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WheelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 设置滚轮滑动停止时事件，监听滚轮选中项
     *
     * @param onWheelItemSelectedListener
     */
    public void setOnWheelItemSelectedListener(OnWheelItemSelectedListener<T>
                                                       onWheelItemSelectedListener) {
        mOnWheelItemSelectedListener = onWheelItemSelectedListener;
    }

    /**
     * 设置滚轮选中项点击事件
     *
     * @param onWheelItemClickListener
     */
    public void setOnWheelItemClickListener(OnWheelItemClickListener<T> onWheelItemClickListener) {
        mOnWheelItemClickListener = onWheelItemClickListener;
    }

    /**
     * 初始化
     */
    private void init() {
        if (mStyle == null) {
            mStyle = new WheelViewStyle();
        }

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        setTag(WheelConstants.TAG);
        setVerticalScrollBarEnabled(false);
        setScrollingCacheEnabled(false);
        setCacheColorHint(Color.TRANSPARENT);
        setFadingEdgeLength(0);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setDividerHeight(0);
        setOnItemClickListener(mOnItemClickListener);
        setOnScrollListener(mOnScrollListener);
        setOnTouchListener(mTouchListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setNestedScrollingEnabled(true);
        }
        addOnGlobalLayoutListener();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
    }

    private void addOnGlobalLayoutListener() {
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver
                .OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
                
                if (getChildCount() > 0 && mItemH == 0) {
                    mItemH = getChildAt(0).getHeight();
                    if (mItemH != 0) {
                        ViewGroup.LayoutParams params = getLayoutParams();
                        params.height = mItemH * mWheelSize;
                        refreshVisibleItems(getFirstVisiblePosition(),
                                getCurrentPosition() + mWheelSize / 2,
                                mWheelSize / 2);
                        setWheelMask();
                    } else {
                        throw new WheelViewException("wheel item is error.");
                    }
                }
            }
        });
    }

    /**
     * 获得滚轮样式
     *
     * @return
     */
    public WheelViewStyle getStyle() {
        return mStyle;
    }

    /**
     * 设置滚轮样式
     *
     * @param style
     */
    public void setStyle(WheelViewStyle style) {
        mStyle = style;
    }

    /**
     * 设置背景
     */
    private void setWheelMask() {
       
        WheelMask wheelBackgroundMask = mStyle.wheelBackground;
        wheelBackgroundMask.setParentWidth(getWidth());
        wheelBackgroundMask.setParentHeight(mItemH * mWheelSize);
        wheelBackgroundMask.setItemHeight(mItemH);
        wheelBackgroundMask.setWheelItemSize(mWheelSize);
        wheelBackgroundMask.preDraw();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            setBackground(wheelBackgroundMask);
        } else {
            setBackgroundDrawable(wheelBackgroundMask);
        }

        WheelMask wheelForegroundMask = mStyle.wheelForegroundMask;
        wheelForegroundMask.setParentWidth(getWidth());
        wheelForegroundMask.setParentHeight(mItemH * mWheelSize);
        wheelForegroundMask.setItemHeight(mItemH);
        wheelForegroundMask.setWheelItemSize(mWheelSize);
        wheelForegroundMask.preDraw();
        setForeground(wheelForegroundMask);

    }

    /**
     * 设置滚轮个数
     *
     * @param wheelSize
     */
    @Override
    public void setWheelSize(int wheelSize) {
        if ((wheelSize & 1) == 0) {
            throw new WheelViewException("wheel size must be an odd number.");
        }
        mWheelSize = wheelSize;
        if (mWheelAdapter != null) {
            mWheelAdapter.setWheelSize(wheelSize);
        }
    }

    /**
     * 设置滚轮是否循环滚动
     *
     * @param loop
     */
    @Override
    public void setLoop(boolean loop) {
        if (loop != mLoop) {
            mLoop = loop;
            setSelection(0);
            if (mWheelAdapter != null) {
                mWheelAdapter.setLoop(loop);
            }
        }
    }

    /**
     * 设置滚轮选中项是否可点击
     *
     * @param clickable
     */
    @Override
    public void setWheelClickable(boolean clickable) {
        if (clickable != mClickable) {
            mClickable = clickable;
            if (mWheelAdapter != null) {
                mWheelAdapter.setClickable(clickable);
            }
        }
    }

    /**
     * 重置数据
     *
     * @param list
     */
    public void resetDataFromTop(final List<T> list) {
        if (WheelUtils.isEmpty(list)) {
            throw new WheelViewException("join map data is error.");
        }
        WheelView.this.postDelayed(new Runnable() {
            @Override
            public void run() {
                setWheelData(list);
                WheelView.super.setSelection(0);
                refreshCurrentPosition(true);
            }
        }, 10);
    }

    /**
     * 获取滚轮位置
     *
     * @return
     */
    public int getSelection() {
        return mSelection;
    }

    /**
     * 设置滚轮位置
     *
     * @param selection
     */
    @Override
    public void setSelection(final int selection) {
        mSelection = selection;
        setVisibility(View.INVISIBLE);
        WheelView.this.postDelayed(new Runnable() {
            @Override
            public void run() {
                WheelView.super.setSelection(getRealPosition(selection));
                refreshCurrentPosition(false);
                setVisibility(View.VISIBLE);
            }
        }, 500);
    }

    /**
     * 连接副WheelView
     *
     * @param wheelView
     */
    @Override
    public void join(WheelView wheelView) {
        if (wheelView == null) {
            throw new WheelViewException("wheelview cannot be null.");
        }
        mJoinWheelView = wheelView;
    }

    /**
     * 副WheelView数据
     *
     * @param map
     */
    public void joinDatas(HashMap<String, List<T>> map) {
        mJoinMap = map;
    }

    /**
     * 获得滚轮当前真实位置
     *
     * @param positon
     * @return
     */
    private int getRealPosition(int positon) {
        if (WheelUtils.isEmpty(mList)) {
            return 0;
        }
        if (mLoop) {
            int d = Integer.MAX_VALUE / 2 / mList.size();
            return positon + d * mList.size() - mWheelSize / 2;
        }
        return positon;
    }

    /**
     * 获取当前滚轮位置
     *
     * @return
     */
    public int getCurrentPosition() {
        return mCurrentPositon;
    }

    /**
     * 获取当前滚轮位置的数据
     *
     * @return
     */
    public T getSelectionItem() {
        int position = getCurrentPosition();
        position = position < 0 ? 0 : position;
        if (mList != null && mList.size() > position) {
            return mList.get(position);
        }
        return null;
    }

    /**
     * 设置滚轮数据适配器，已弃用，具体使用{@link #setWheelAdapter(BaseWheelAdapter)}
     *
     * @param adapter
     */
    @Override
    @Deprecated
    public void setAdapter(ListAdapter adapter) {
        if (adapter != null && adapter instanceof BaseWheelAdapter) {
            setWheelAdapter((BaseWheelAdapter) adapter);
        } else {
            throw new WheelViewException("please invoke setWheelAdapter " +
                    "method.");
        }
    }

    /**
     * 设置滚轮数据源适配器
     *
     * @param adapter
     */
    @Override
    public void setWheelAdapter(BaseWheelAdapter<T> adapter) {
        super.setAdapter(adapter);
        mWheelAdapter = adapter;
        mWheelAdapter.setData(mList).setWheelSize(mWheelSize).setLoop(mLoop).setClickable(mClickable);
    }

    /**
     * 设置滚轮数据
     *
     * @param list
     */
    @Override
    public void setWheelData(List<T> list) {
        if (WheelUtils.isEmpty(list)) {
            throw new WheelViewException("wheel datas are error.");
        }
        mList = list;
        if (mWheelAdapter != null) {
            mWheelAdapter.setData(list);
        }
    }

    /**
     * 设置选中行附加文本
     *
     * @param text
     * @param textColor
     * @param textSize
     * @param margin
     */
    public void setExtraText(String text, int textColor, int textSize, int
            margin) {
        mExtraText = text;
        mExtraTextColor = textColor;
        mExtraTextSize = textSize;
        mExtraMargin = margin;
    }

    /**
     * 获得滚轮数据总数
     *
     * @return
     */
    public int getWheelCount() {
        return !WheelUtils.isEmpty(mList) ? mList.size() : 0;
    }

    /**
     * 平滑的滚动距离
     *
     * @param scrollDistance
     * @return
     */
    public int getSmoothDistance(float scrollDistance) {
        if (Math.abs(scrollDistance) <= 2) {
            return (int) scrollDistance;
        } else if (Math.abs(scrollDistance) < 12) {
            return scrollDistance > 0 ? 2 : -2;
        } else {
            return (int) (scrollDistance / 6);  // 减缓平滑滑动速率
        }
    }

    /**
     * 刷新当前位置
     *
     * @param join
     */
    public void refreshCurrentPosition(boolean join) {
        if (getChildAt(0) == null || mItemH == 0) {
            return;
        }
        int firstPosition = getFirstVisiblePosition();
        if (mLoop && firstPosition == 0) {
            return;
        }
        int position = 0;
        if (Math.abs(getChildAt(0).getY()) <= mItemH / 2) {
            position = firstPosition;
        } else {
            position = firstPosition + 1;
        }
        
        
        refreshVisibleItems(firstPosition, position + mWheelSize / 2,
                mWheelSize / 2);
        if (mLoop) {
            position = (position + mWheelSize / 2) % getWheelCount();
        }
        if (position == mCurrentPositon && !join) {
            return;
        }
        mCurrentPositon = position;
        mWheelAdapter.setCurrentPosition(position);
        mHandler.removeMessages(WheelConstants.WHEEL_SCROLL_HANDLER_WHAT);
        mHandler.sendEmptyMessageDelayed(WheelConstants
                .WHEEL_SCROLL_HANDLER_WHAT, WheelConstants
                .WHEEL_SCROLL_DELAY_DURATION);
    }

    /**
     * 刷新可见滚动列表
     *
     * @param firstPosition
     * @param curPosition
     * @param offset
     */
    private void refreshVisibleItems(int firstPosition, int curPosition, int
            offset) {
        for (int i = curPosition - offset; i <= curPosition + offset; i++) {
            View itemView = getChildAt(i - firstPosition);
            if (itemView == null) {
                continue;
            }
            TextView textView = WheelUtils.findTextView(itemView);
            if (textView != null) {
                refreshTextView(i, curPosition, itemView, textView);
            }
        }
    }

    /**
     * 刷新文本
     *
     * @param position
     * @param curPosition
     * @param itemView
     * @param textView
     */
    private void refreshTextView(int position, int curPosition, View
            itemView, TextView textView) {
        if (curPosition == position) { // 选中
            int textColor = mStyle.selectedTextColor;
            float defTextSize = mStyle.textSize;
            float textSize = defTextSize * mStyle.selectedTextZoom;
            setTextView(itemView, textView, textColor, textSize, 1.0f);
        } else {    // 未选中
            int textColor =mStyle.textColor;
            float textSize = mStyle.textSize;
            int delta = Math.abs(position - curPosition);
            float alpha = (float) Math.pow(mStyle.textAlpha, delta);
            setTextView(itemView, textView, textColor, textSize, alpha);
        }
    }

    /**
     * 设置TextView
     *
     * @param itemView
     * @param textView
     * @param textColor
     * @param textSize
     * @param textAlpha
     */
    private void setTextView(View itemView, TextView textView, int textColor, float textSize, float textAlpha) {
        textView.setTextColor(textColor);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
        itemView.setAlpha(textAlpha);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!TextUtils.isEmpty(mExtraText)) {
            Rect targetRect = new Rect(0, mItemH * (mWheelSize / 2), getWidth
                    (), mItemH * (mWheelSize / 2 + 1));
            mTextPaint.setTextSize(mExtraTextSize);
            mTextPaint.setColor(mExtraTextColor);
            Paint.FontMetricsInt fontMetrics = mTextPaint.getFontMetricsInt();
            int baseline = (targetRect.bottom + targetRect.top - fontMetrics
                    .bottom - fontMetrics.top) / 2;
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(mExtraText, targetRect.centerX() + mExtraMargin,
                    baseline, mTextPaint);
        }
    }

  

    public interface OnWheelItemSelectedListener<T> {
        void onItemSelected(int position, T t);
    }

    public interface OnWheelItemClickListener<T> {
        void onItemClick(int position, T t);
    }

    public static class WheelViewStyle {

        private int backgroundColor; // 背景颜色
        private int holoBorderColor;   // holo样式边框颜色
        private int textColor; // 文本颜色
        private int selectedTextColor; // 选中文本颜色
        private int textSize;// 文本大小
        private int selectedTextSize;   // 选中文本大小
        private float textAlpha;  // 文本透明度(0f ~ 1f)
        private float selectedTextZoom; // 选中文本放大倍数
        private WheelMask wheelForegroundMask;
        private WheelMask wheelBackground;

        protected WheelViewStyle() {
            
        }

        public WheelViewStyle(WheelViewStyle style) {
            this.backgroundColor = style.backgroundColor;
            this.holoBorderColor = style.holoBorderColor;
            this.textColor = style.textColor;
            this.selectedTextColor = style.selectedTextColor;
            this.textSize = style.textSize;
            this.selectedTextSize = style.selectedTextSize;
            this.textAlpha = style.textAlpha;
            this.selectedTextZoom = style.selectedTextZoom;
        }

        public int getBackgroundColor() {
            return backgroundColor;
        }

        public int getHoloBorderColor() {
            return holoBorderColor;
        }

        public int getUnselectedTextColor() {
            return textColor;
        }

        public int getSelectedTextColor() {
            return selectedTextColor;
        }

        public int getUnselectedTextSize() {
            return textSize;
        }

        public int getSelectedTextSize() {
            return selectedTextSize;
        }

        public float getTextAlpha() {
            return textAlpha;
        }

        public float getSelectedTextZoom() {
            return selectedTextZoom;
        }

        public WheelMask getWheelForegroundMask() {
            return wheelForegroundMask;
        }
    }
    
    public static  class WheelStyleBuilder {
        
        private int unselectedTextColor;
        private int selectedTextColor;
        private int unselectedTextSize;
        private int selectedTextSize;
        private float textAlpha;
        private float selectedTextZoom;

        private Context context;
        private WheelMask wheelForegroundMask;
        private WheelMask wheelBackground;
        
        
        public WheelStyleBuilder(Context context) {
            context = context;
            selectedTextColor = unselectedTextColor = WheelConstants.WHEEL_TEXT_COLOR;
            selectedTextSize = unselectedTextSize = WheelConstants.WHEEL_TEXT_SIZE;
            selectedTextZoom = 1;
            textAlpha = 1;
            wheelForegroundMask = new WheelMask();
            wheelBackground = new WheelMask();
        }
        
        public WheelStyleBuilder unselectedTextColor(int color){
            unselectedTextColor = color;
            return this;
        }

        
        public WheelStyleBuilder unselectedTextSize(int size){
            unselectedTextSize = size;
            return this;
        }


        public WheelStyleBuilder selectedTextColor(int color){
            selectedTextColor = color;
            return this;
        }

        public WheelStyleBuilder selectedTextSize(int size){
            unselectedTextColor = size;
            return this;
        }

        public WheelStyleBuilder textAlpha(@FloatRange(from=0.0, to=1.0) float alpha){
            textAlpha = alpha;
            return this;
        }


        public WheelStyleBuilder selectedTextZoom(float offset){
            selectedTextZoom = offset;
            return this;
        }

        public WheelStyleBuilder wheelBackground(WheelMask  wheelMask){
            wheelBackground = wheelMask;
            return this;
        }
        
        
        public WheelStyleBuilder wheelForegroundMask(WheelMask wheelMask){
            this.wheelForegroundMask = wheelMask;
            return this;
        }
        
        
        
        public WheelViewStyle build(){
            WheelViewStyle wheelViewStyle = new WheelViewStyle();
            wheelViewStyle.selectedTextSize = selectedTextSize;
            wheelViewStyle.textSize = unselectedTextSize;
            wheelViewStyle.selectedTextColor = selectedTextColor;
            wheelViewStyle.textColor = unselectedTextColor;
            wheelViewStyle.textAlpha = textAlpha;
            wheelViewStyle.selectedTextZoom = selectedTextZoom;
            wheelViewStyle.wheelForegroundMask = wheelForegroundMask;
            wheelViewStyle.wheelBackground = wheelBackground;
            return wheelViewStyle;
        }
    }


    private Drawable mWheelForeground;
    
    private final Rect mSelfBounds = new Rect();
    private final Rect mOverlayBounds = new Rect();

    private int mForegroundGravity = Gravity.FILL;

    protected boolean mForegroundInPadding = true;

    boolean mForegroundBoundsChanged = false;


    /**
     * Describes how the foreground is positioned.
     *
     * @return foreground gravity.
     *
     * @see #setForegroundGravity(int)
     */
    public int getForegroundGravity() {
        return mForegroundGravity;
    }

    /**
     * Describes how the foreground is positioned. Defaults to START and TOP.
     *
     * @param foregroundGravity See {@link android.view.Gravity}
     *
     * @see #getForegroundGravity()
     */
    public void setForegroundGravity(int foregroundGravity) {
        if (mForegroundGravity != foregroundGravity) {
            if ((foregroundGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) {
                foregroundGravity |= Gravity.START;
            }

            if ((foregroundGravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) {
                foregroundGravity |= Gravity.TOP;
            }

            mForegroundGravity = foregroundGravity;


            if (mForegroundGravity == Gravity.FILL && mWheelForeground != null) {
                Rect padding = new Rect();
                mWheelForeground.getPadding(padding);
            }

            requestLayout();
        }
    }

    @Override
    public boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || (who == mWheelForeground);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mWheelForeground != null) mWheelForeground.jumpToCurrentState();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mWheelForeground != null && mWheelForeground.isStateful()) {
            mWheelForeground.setState(getDrawableState());
        }
    }

    /**
     * Supply a Drawable that is to be rendered on top of all of the child
     * views in the frame layout.  Any padding in the Drawable will be taken
     * into account by ensuring that the children are inset to be placed
     * inside of the padding area.
     *
     * @param drawable The Drawable to be drawn on top of the children.
     */
    public void setForeground(Drawable drawable) {
        if (mWheelForeground != drawable) {
            if (mWheelForeground != null) {
                mWheelForeground.setCallback(null);
                unscheduleDrawable(mWheelForeground);
            }

            mWheelForeground = drawable;

            if (drawable != null) {
                setWillNotDraw(false);
                drawable.setCallback(this);
                if (drawable.isStateful()) {
                    drawable.setState(getDrawableState());
                }
                if (mForegroundGravity == Gravity.FILL) {
                    Rect padding = new Rect();
                    drawable.getPadding(padding);
                }
            }  else {
                setWillNotDraw(true);
            }
            requestLayout();
            invalidate();
        }
    }

    /**
     * Returns the drawable used as the foreground of this FrameLayout. The
     * foreground drawable, if non-null, is always drawn on top of the children.
     *
     * @return A Drawable or null if no foreground was set.
     */
    public Drawable getForeground() {
        return mWheelForeground;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mForegroundBoundsChanged = changed;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mForegroundBoundsChanged = true;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (mWheelForeground != null) {
            final Drawable foreground = mWheelForeground;

            if (mForegroundBoundsChanged) {
                mForegroundBoundsChanged = false;
                final Rect selfBounds = mSelfBounds;
                final Rect overlayBounds = mOverlayBounds;

                final int w = getRight() - getLeft();
                final int h = getBottom() - getTop();

                if (mForegroundInPadding) {
                    selfBounds.set(0, 0, w, h);
                } else {
                    selfBounds.set(getPaddingLeft(), getPaddingTop(),
                            w - getPaddingRight(), h - getPaddingBottom());
                }

                Gravity.apply(mForegroundGravity, foreground.getIntrinsicWidth(),
                        foreground.getIntrinsicHeight(), selfBounds, overlayBounds);
                foreground.setBounds(overlayBounds);
            }

            foreground.draw(canvas);
        }
    }

}
