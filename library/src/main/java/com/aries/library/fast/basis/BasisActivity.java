package com.aries.library.fast.basis;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import com.aries.library.fast.FastConfig;
import com.aries.library.fast.entity.FastNavigationConfigEntity;
import com.aries.library.fast.entity.FastQuitConfigEntity;
import com.aries.library.fast.i.IBasisView;
import com.aries.library.fast.i.NavigationBarControl;
import com.aries.library.fast.manager.LoggerManager;
import com.aries.library.fast.manager.RxJavaManager;
import com.aries.library.fast.util.FastStackUtil;
import com.aries.library.fast.util.FastUtil;
import com.aries.library.fast.util.NavigationBarUtil;
import com.aries.library.fast.util.SnackBarUtil;
import com.aries.library.fast.util.ToastUtil;
import com.trello.rxlifecycle2.android.ActivityEvent;
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity;

import org.simple.eventbus.EventBus;

import butterknife.ButterKnife;
import butterknife.Unbinder;
import cn.bingoogolapple.swipebacklayout.BGASwipeBackHelper;

/**
 * Created: AriesHoo on 2017/7/19 15:37
 * E-Mail: AriesHoo@126.com
 * Function: 所有Activity的基类
 * Description:
 */
public abstract class BasisActivity extends RxAppCompatActivity implements IBasisView {

    protected Activity mContext;
    protected View mContentView;
    protected Unbinder mUnBinder;
    protected BGASwipeBackHelper mSwipeBackHelper;

    protected boolean mIsViewLoaded = false;
    protected boolean mIsFirstShow = true;
    protected boolean mIsFirstBack = true;
    private boolean mIsGlobal = false;
    protected long mDelayBack = 2000;
    protected final String TAG = getClass().getSimpleName();
    protected FastQuitConfigEntity mQuitEntity;

    protected NavigationBarControl getNavigationBarControl() {
        return FastConfig.getInstance(this).getNavigationBarControl();
    }

    protected View getNavigationBarControlView() {
        return mContentView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EventBus.getDefault().register(this);
        LoggerManager.i(TAG, "getRequestedOrientation:" + (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
        //先判断xml没有设置屏幕模式避免将开发者本身想设置的覆盖掉
        if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            setRequestedOrientation(getOrientation());
        }
        super.onCreate(savedInstanceState);
        mContext = this;
        FastStackUtil.getInstance().push(this);
        initSwipeBack();
        beforeSetContentView();
        mContentView = View.inflate(mContext, getContentLayout(), null);
        setContentView(mContentView);
        mUnBinder = ButterKnife.bind(this);
        setNavigationBar();
        mIsViewLoaded = true;
        beforeInitView();
        initView(savedInstanceState);
    }

    private void setNavigationBar() {
        if (getNavigationBarControl() == null) {
            return;
        }
        FastNavigationConfigEntity entity = getNavigationBarControl().createNavigationBarControl(this);
        if (entity == null || !entity.isControlEnable()) {
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && entity.isTransEnable()) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            getWindow().setNavigationBarColor(entity.getColor());
        }
        final View controlView = getNavigationBarControlView();
        if (controlView != null && NavigationBarUtil.hasSoftKeys(getWindowManager())) {
            controlView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (mIsGlobal) {
                        return;
                    }
                    mIsGlobal = true;
                    ViewGroup.LayoutParams params = controlView.getLayoutParams();
                    if (params != null && params.height >= 0) {//默认
                        params.height = params.height + NavigationBarUtil.getNavigationBarHeight(getWindowManager());
                    }
                    controlView.setPadding(controlView.getPaddingLeft(), controlView.getPaddingTop(), controlView.getPaddingRight(),
                            controlView.getPaddingBottom() + NavigationBarUtil.getNavigationBarHeight(getWindowManager()));
                }
            });
        }
    }

    @Override
    protected void onResume() {
        beforeLazyLoad();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
        if (mUnBinder != null) {
            mUnBinder.unbind();
        }
        FastStackUtil.getInstance().pop(this, false);
    }

    /**
     * 是否开启滑动返回
     */
    protected boolean isSwipeBackEnable() {
        return FastConfig.getInstance(this).isSwipeBackEnable();
    }

    @Override
    public int getContentBackground() {
        return FastConfig.getInstance(this).getContentViewBackgroundResource();
    }

    /**
     * 设置屏幕方向
     * 默认自动 ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
     * 竖屏 ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
     * 横屏 ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
     * {@link ActivityInfo#screenOrientation ActivityInfo.screenOrientation}
     *
     * @return
     */
    public int getOrientation() {
        return FastConfig.getInstance(this).getRequestedOrientation();
    }

    /**
     * 初始化滑动返回
     */
    private void initSwipeBack() {
        if (!FastUtil.isClassExist("cn.bingoogolapple.swipebacklayout.BGASwipeBackHelper")) {
            LoggerManager.e(TAG, "initSwipeBack:Please compile 'cn.bingoogolapple:bga-swipebacklayout:1.1.1@aar' in app main program");
            return;
        }
        if (isSwipeBackEnable()) {
            mSwipeBackHelper = new BGASwipeBackHelper(this, new BGASwipeBackHelper.Delegate() {
                @Override
                public boolean isSupportSwipeBack() {
                    return true;
                }

                @Override
                public void onSwipeBackLayoutSlide(float slideOffset) {
                }

                @Override
                public void onSwipeBackLayoutCancel() {

                }

                @Override
                public void onSwipeBackLayoutExecuted() {
                    //滑动返回执行完毕，销毁当前 Activity
                    mSwipeBackHelper.swipeBackward();
                }
            });
        }
    }

    @Nullable
    public <T extends View> T findViewByViewId(@IdRes int viewId) {
        return (T) findViewById(viewId);
    }

    @Override
    public void beforeSetContentView() {
        mQuitEntity = FastConfig.getInstance(this).getQuitConfig();
        mDelayBack = mQuitEntity.getQuitDelay();
    }

    @Override
    public void beforeInitView() {
        if (getContentBackground() > 0) {
            mContentView.setBackgroundResource(getContentBackground());
        }
    }

    @Override
    public void loadData() {

    }

    private void beforeLazyLoad() {
        if (!mIsViewLoaded) {//确保视图加载及视图绑定完成避免刷新UI抛出异常
            RxJavaManager.getInstance().setTimer(10, new RxJavaManager.TimerListener() {
                @Override
                public void timeEnd() {
                    beforeLazyLoad();
                }
            });
        } else {
            lazyLoad();
        }
    }

    private void lazyLoad() {
        if (mIsFirstShow) {
            mIsFirstShow = false;
            loadData();
        }
    }

    protected void quitApp() {
        quitApp(mQuitEntity.isSnackBarEnable(), mQuitEntity.isBackToTaskEnable());
    }

    /**
     * 退出程序
     *
     * @param isSnackBar
     */
    protected void quitApp(boolean isSnackBar, boolean isBackToTask) {
        if (isBackToTask && !mQuitEntity.isBackToTaskDelayEnable()) {//设置退回桌面且不等待
            moveTaskToBack(true);
            return;
        }
        if (mIsFirstBack) {
            if (isSnackBar) {
                SnackBarUtil.with(mContentView)
                        .setBgColor(mQuitEntity.getSnackBarBackgroundColor())
                        .setMessageColor(mQuitEntity.getSnackBarMessageColor())
                        .setMessage(mQuitEntity.getQuitMessage())
                        .show();
            } else {
                ToastUtil.show(mQuitEntity.getQuitMessage());
            }
            mIsFirstBack = false;
            RxJavaManager.getInstance().setTimer(mDelayBack, new RxJavaManager.TimerListener() {
                @Override
                public void timeEnd() {
                    mIsFirstBack = true;
                }
            }).compose(bindUntilEvent(ActivityEvent.DESTROY));
        } else {
            if (isBackToTask) {
                moveTaskToBack(true);
            } else {
                FastStackUtil.getInstance().exit();
            }
        }
    }
}
