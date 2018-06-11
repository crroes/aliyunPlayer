package com.cross.fxwiz_widget_player.view;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.aliyun.vodplayer.media.AliyunVodPlayer;
import com.aliyun.vodplayer.media.IAliyunVodPlayer;
import com.cross.fxwiz_widget_player.R;
import com.cross.fxwiz_widget_player.utils.IPlayerContext;
import com.cross.fxwiz_widget_player.utils.MediaBean;
import com.cross.fxwiz_widget_player.utils.OnClickNoMutiListener;
import com.cross.fxwiz_widget_player.utils.PermissionUtils;
import com.cross.fxwiz_widget_player.utils.PlayerState;
import com.cross.fxwiz_widget_player.utils.PlayerUiControls;
import com.cross.fxwiz_widget_player.utils.PlayerUtils;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by cross on 2018/5/14.
 * <p>描述: 播放器基类
 */

abstract class BasePlayerView extends FrameLayout implements PlayerUiControls, IPlayerContext {

	protected final String TAG = this.getClass().getName();
	protected AliyunVodPlayer mAliyunPlayer;
	private SurfaceView mSurfaceView;
	protected int mCurrentState = PlayerState.CURRENT_STATE_NORMAL; //播放状态
	protected int mCurrentScreenState;//屏幕状态
	protected int mCurrentScreenLockState = PlayerState.SCREEN_WINDOW_UNLOCK;//ui锁状态
	protected boolean isMoving;//是否正在滑动进度
	private Timer HIDE_CONTROL_VIEW_TIMER;//屏幕隐藏任务timer


	public ImageView backButton;
	public TextView titleTextView;
	protected ImageView shareImageView;
	public ImageView startButton;
	public ImageView fullscreenButton;
	public ViewGroup bottomContainer;

	public ProgressBar loadingProgressBar;
	public ImageView thumbImageView;
	public TextView replayTextView;
	public TextView clarity;
	public TextView errorRetryBtn;
	private ImageButton lockButton;
	public LinearLayout errorRetryLayout;

	protected int mScreenWidth;
	protected int mScreenHeight;
	private int mInitialWidth;
	private int mInitialHeight;
	protected AudioManager mAudioManager;

	public ViewGroup textureViewContainer;
	public ViewGroup topContainer;
	private HideUiTimerTask mHideUiTimerTask;

	//全屏时使用到的参数
	private ViewGroup mContainerView;

	//播放器状态监听器
	private BasePlayerFragment.OnPlayerStatusChangeListener mOnPlayerStatusChangeListener;
	protected MediaBean mMediaBean;//视频信息bean
	private boolean isContextBackground; //载体context是否处于后台


	public BasePlayerView(@NonNull Context context) {
		this(context, null);
	}

	public BasePlayerView(@NonNull Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public BasePlayerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	protected abstract int getLayoutId();

	private void initView() {
		titleTextView = (TextView) findViewById(R.id.title);//标题
		backButton = (ImageView) findViewById(R.id.back);//返回箭头
		shareImageView = (ImageView) findViewById(R.id.share);//分享图标
		startButton = (ImageView) findViewById(R.id.start);
		fullscreenButton = (ImageView) findViewById(R.id.fullscreen);//全屏按钮
		thumbImageView = (ImageView) findViewById(R.id.thumb);//首帧图片
		loadingProgressBar = (ProgressBar) findViewById(R.id.loading);//加载进度动画
		replayTextView = (TextView) findViewById(R.id.replay_text);//重播text
		clarity = (TextView) findViewById(R.id.clarity);//透明？
		errorRetryBtn = (TextView) findViewById(R.id.error_retry_btn);//点击重试
		errorRetryLayout = (LinearLayout) findViewById(R.id.error_layout);//视频加载失败
		lockButton = (ImageButton) findViewById(R.id.ib_lock); //锁
		bottomContainer = (ViewGroup) findViewById(R.id.layout_bottom);//底部容器


		loadingProgressBar.setVisibility(VISIBLE);
		startButton.setVisibility(INVISIBLE);
		thumbImageView.setVisibility(GONE);

		shareImageView.setOnClickListener(mOnClickListener);
		fullscreenButton.setOnClickListener(mOnClickListener);
		lockButton.setOnClickListener(mOnClickListener);
		backButton.setOnClickListener(mOnClickListener);
		errorRetryBtn.setOnClickListener(mOnClickListener);
		startButton.setOnClickListener(mOnClickListener);
		if (clarity != null) {
			clarity.setOnClickListener(mOnClickListener);
		}

		textureViewContainer = (ViewGroup) findViewById(R.id.surface_container);
		topContainer = (ViewGroup) findViewById(R.id.layout_top);
		textureViewContainer.setOnClickListener(mOnClickListener);//视图窗口的点击事件

		mScreenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
		mScreenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
		mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);

	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {

		switch (ev.getAction()) {
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_UP:
				if (mCurrentState == PlayerState.CURRENT_STATE_PLAYING || mCurrentScreenState == PlayerState.CURRENT_STATE_PAUSE) {
					if (mCurrentScreenState == PlayerState.SCREEN_WINDOW_FULLSCREEN) {
						lockButton.setVisibility(VISIBLE);
					}
					if (mCurrentScreenLockState != PlayerState.SCREEN_WINDOW_LOCK) {
						changeUiToPlayingShow();
					}
					startHideUiTimer();
				}
				break;
		}
		return super.dispatchTouchEvent(ev);
	}

	/**
	 * 初始化自定义View视图
	 */
	private void init() {

		View.inflate(getContext(), getLayoutId(), this);
		mAliyunPlayer = new AliyunVodPlayer(getContext());
		mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);

		initView();

		mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {

			public void surfaceCreated(SurfaceHolder holder) {
				//holder.setType(SurfaceHolder.SURFACE_TYPE_GPU);
				holder.setKeepScreenOn(true);
				Log.i(TAG, "AlivcPlayer onSurfaceCreated." + mAliyunPlayer);

				// Important: surfaceView changed from background to front, we need reset surface to mediaplayer.
				// 对于从后台切换回到页面,需要重设surface
				if (mAliyunPlayer != null) {
					mAliyunPlayer.setSurface(mSurfaceView.getHolder().getSurface());
				}

				//TODO 如果是锁屏状态，继续播放并且

				//1.正在播放中按home回到手机主屏幕重新进来继续播放
				//2.暂停中按home回到手机主屏幕重新进来还是暂停状态
				//3.加载中按home回到手机主屏幕重新进来（未加载好继续加载，加载完成暂停）
				if (mCurrentState == PlayerState.CURRENT_STATE_PAUSE) {
					//暂停退到后台再重新打开
					changeUiToPauseShow();
				} else if (mCurrentState == PlayerState.CURRENT_STATE_PLAYING) {
					//播放中退到后台再重新打开
					start();
				} else if (mCurrentState == PlayerState.CURRENT_STATE_PREPARING) {
					//加载中中退到后台再重新打开（未加载完成）继续加载
					changeUiToPreparing();
				}

				Log.i(TAG, "AlivcPlayeron SurfaceCreated over.");
			}

			public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
				Log.i(TAG, "onSurfaceChanged is valid ? " + holder.getSurface().isValid());
				if (mAliyunPlayer != null) {
					mAliyunPlayer.surfaceChanged();
				}
			}

			public void surfaceDestroyed(SurfaceHolder holder) {
				Log.i(TAG, "onSurfaceDestroy.");
				mAliyunPlayer.pause();
			}
		});

		//设置播放器监听
		mAliyunPlayer.setOnPreparedListener(new IAliyunVodPlayer.OnPreparedListener() {
			@Override
			public void onPrepared() {
				//准备完成时触发
				Log.i(TAG, "准备完成时触发");
				titleTextView.setText(mMediaBean.getTitle());
				if (isContextBackground) {
					//如果在后台的时候加载好了置换为暂停
					pause();
				} else {
					start();
				}
				mMediaBean.setDuration(mAliyunPlayer.getDuration());

			}
		});

		mAliyunPlayer.setOnFirstFrameStartListener(new IAliyunVodPlayer.OnFirstFrameStartListener() {
			@Override
			public void onFirstFrameStart() {
				//首帧显示触发
				Log.i(TAG, "首帧显示触发");

				mCurrentState = PlayerState.CURRENT_STATE_PLAYING;

				//如果有历史观看
				//1.不是直播
				//2.时间不为0
				//3.时间不等于播放器的当前播放时间（以秒为单位）
				if (mMediaBean.getType() == MediaBean.MediaType.VIDEO && mMediaBean.getCurrentPosition() != 0 && mAliyunPlayer.getCurrentPosition() / 1000 != mMediaBean.getCurrentPosition() / 1000) {
					//继续播放
					mAliyunPlayer.seekTo((int) mMediaBean.getCurrentPosition());
					if (mOnPlayerStatusChangeListener != null) {
						mOnPlayerStatusChangeListener.onContinue(mMediaBean.getCurrentPosition());
					}
				} else {
					//正常播放
					if (mCurrentScreenLockState != PlayerState.SCREEN_WINDOW_LOCK) {
						changeUiToPlayingShow();
						startHideUiTimer();
					}
					if (mOnPlayerStatusChangeListener != null) {
						mOnPlayerStatusChangeListener.onStart();
					}
				}

			}
		});

		mAliyunPlayer.setOnErrorListener(new IAliyunVodPlayer.OnErrorListener() {
			@Override
			public void onError(int arg0, int arg1, String msg) {
				//出错时处理，查看接口文档中的错误码和错误消息
				Log.i(TAG, "出错时处理，查看接口文档中的错误码和错误消息" + "\n" + "arg0 = " + arg1 + "\n" + "arg1 = " + arg1 + "\n" + "msg = " + msg);
				mCurrentState = PlayerState.CURRENT_STATE_ERROR;

				if (mOnPlayerStatusChangeListener != null) {
					mOnPlayerStatusChangeListener.onError(mMediaBean.getCurrentPosition());
				}
				changeUiToError();
			}
		});

		mAliyunPlayer.setOnCompletionListener(new IAliyunVodPlayer.OnCompletionListener() {
			@Override
			public void onCompletion() {
				//播放正常完成时触发
				Log.i(TAG, "播放正常完成时触发");
				mCurrentState = PlayerState.CURRENT_STATE_COMPLETE;
				changeUiToComplete();
				//初始化当前播放时间
				mMediaBean.setCurrentPosition(0);
				if (mOnPlayerStatusChangeListener != null) {
					mOnPlayerStatusChangeListener.onComplete();
				}
			}
		});

		mAliyunPlayer.setOnSeekLiveCompletionListener(new IAliyunVodPlayer.OnSeekLiveCompletionListener() {
			@Override
			public void onSeekLiveCompletion(long l) {
				Log.i(TAG, "seek完成时" + l);
			}
		});
		mAliyunPlayer.setOnSeekCompleteListener(new IAliyunVodPlayer.OnSeekCompleteListener() {
			@Override
			public void onSeekComplete() {
				//seek完成时触发
				Log.i(TAG, "seek完成时触发 + playerTime ：" + PlayerUtils.stringForTime(mAliyunPlayer.getCurrentPosition()) + "\t mediaTime : " + PlayerUtils.stringForTime(mMediaBean.getCurrentPosition()));
				if (mCurrentState == PlayerState.CURRENT_STATE_PAUSE) {
					start();
				} else {
					mCurrentState = PlayerState.CURRENT_STATE_PLAYING;
					changeUiToPlayingShow();
				}

			}
		});
		mAliyunPlayer.setOnStoppedListner(new IAliyunVodPlayer.OnStoppedListener() {
			@Override
			public void onStopped() {
				//使用stop功能时触发
				Log.i(TAG, "使用stop功能时触发");
				mCurrentState = PlayerState.CURRENT_STATE_STOP;
				if (mOnPlayerStatusChangeListener != null) {
					mOnPlayerStatusChangeListener.onStop(mMediaBean.getCurrentPosition());
				}
			}
		});

		mAliyunPlayer.setOnChangeQualityListener(new IAliyunVodPlayer.OnChangeQualityListener() {
			@Override
			public void onChangeQualitySuccess(String finalQuality) {
				//清晰度切换成功时触发
				Log.i(TAG, "清晰度切换成功时触发");
			}

			@Override
			public void onChangeQualityFail(int code, String msg) {
				//清晰度切换失败时触发
				Log.i(TAG, "清晰度切换失败时触发");
			}
		});
		mAliyunPlayer.setOnCircleStartListener(new IAliyunVodPlayer.OnCircleStartListener() {
			@Override
			public void onCircleStart() {
				//循环播放开始
				Log.i(TAG, "循环播放开始");
			}
		});

		initChild();

	}

	/**
	 * 更新开始暂停按钮
	 */
	public void updateStartImage() {

		switch (mCurrentState) {
			case PlayerState.CURRENT_STATE_PREPARING:
				startButton.setVisibility(INVISIBLE);
				loadingProgressBar.setVisibility(VISIBLE);
				break;
			case PlayerState.CURRENT_STATE_PLAYING:
				startButton.setVisibility(VISIBLE);
				startButton.setImageResource(R.drawable.player_click_pause_selector);
				break;
			case PlayerState.CURRENT_STATE_PAUSE:
				startButton.setVisibility(VISIBLE);
				startButton.setImageResource(R.drawable.player_click_play_selector);
				break;
			case PlayerState.CURRENT_STATE_COMPLETE:
				startButton.setVisibility(VISIBLE);
				startButton.setImageResource(R.drawable.player_click_replay_selector);
				break;
			case PlayerState.CURRENT_STATE_ERROR:
				startButton.setVisibility(INVISIBLE);
				break;
		}

	}


	/**
	 * 同意权限时重新初始化
	 */
	public void onPermissionSuccess() {
		play();
	}

	/**
	 * 子类可重写，在基类初始化方法最后调用
	 */
	protected void initChild() {
	}

	/**
	 * 设置播放源
	 *
	 * @param mediaBean {@link MediaBean}
	 */
	public void setUp(@NonNull MediaBean mediaBean) {

		mMediaBean = mediaBean;
		PermissionUtils permission = new PermissionUtils(getContext(), true);
		boolean hasPermission = permission.checkPermissions(PermissionUtils.REQUEST_STORAGE_PERMISSION, PermissionUtils.STORAGE_PERMISSION);
		if (!hasPermission) {
			//在没有权限的时候将申请权限并不做初始化，适配同意权限后播放需要在载体activity中的权限回调方法中调用onPermissionSuccess（）
			return;
		}
		play();
		if (!mediaBean.isShareIconHide()) {
			shareImageView.setVisibility(VISIBLE);
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (mInitialWidth == 0) {

			mInitialHeight = getMeasuredHeight();
			mInitialWidth = getMeasuredWidth();
		}
	}

	/**
	 * 监听器
	 */
	private OnClickNoMutiListener mOnClickListener = new OnClickNoMutiListener() {
		@Override
		public void onClick(View v) {
			if (isMultipleClick()) {
				return;
			}
			int vId = v.getId();
			if (vId == R.id.start) {
				//点击开始或暂停
				if (mCurrentState == PlayerState.CURRENT_STATE_NORMAL) {
					start();
				} else if (mCurrentState == PlayerState.CURRENT_STATE_PLAYING) {
					pause();
				} else if (mCurrentState == PlayerState.CURRENT_STATE_PAUSE) {
					start();
				} else if (mCurrentState == PlayerState.CURRENT_STATE_COMPLETE) {
					play();
				}
			} else if (vId == R.id.fullscreen) {
				//点击全屏切换
				if (mCurrentState == PlayerState.CURRENT_STATE_COMPLETE)
					return;
				if (mCurrentScreenState == PlayerState.SCREEN_WINDOW_FULLSCREEN) {
					startWindowVertical();
				} else {
					startWindowHorizontal();
				}
			} else if (vId == R.id.back) {
				//点击回退箭头
				if (mCurrentScreenState == PlayerState.SCREEN_WINDOW_FULLSCREEN) {
					startWindowVertical();//回到竖屏
				} else if (mCurrentScreenState == PlayerState.SCREEN_WINDOW_NORMAL && PlayerUtils.getAppCompActivity(getContext()) != null) {
					((FragmentActivity) getContext()).finish();
				}
			} else if (vId == R.id.ib_lock) {
				//切换锁屏状态
				changeLockUi();
			} else if (vId == R.id.error_retry_btn) {
				//重新加载
				play();
			} else if (vId == R.id.share) {
				//点击分享
				if (mMediaBean.getOnClickActionListener() != null) {
					mMediaBean.getOnClickActionListener().onClickShareIcon();
				}
			}
		}
	};

	//设置横屏
	private void startWindowHorizontal() {
		FragmentActivity appCompActivity = PlayerUtils.getAppCompActivity(getContext());
		if (appCompActivity != null) {
			appCompActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
	}

	//设置竖屏
	private void startWindowVertical() {
		FragmentActivity appCompActivity = PlayerUtils.getAppCompActivity(getContext());
		if (appCompActivity != null) {
			appCompActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}

	}

	/**
	 * activity监听屏幕方向切换是调用此方法
	 *
	 * @param newConfig {@link Configuration}
	 */
	public void onActivityConfigurationChanged(Configuration newConfig) {
		//切换为竖屏
		if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
			Log.i(TAG, " 切换为竖屏");
			mCurrentScreenState = PlayerState.SCREEN_WINDOW_NORMAL;
			fullscreenButton.setImageResource(R.drawable.player_enlarge);
		}
		//切换为横屏
		else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			Log.i(TAG, "切换为横屏");
			mCurrentScreenState = PlayerState.SCREEN_WINDOW_FULLSCREEN;
			fullscreenButton.setImageResource(R.drawable.player_shrink);
		}
		onScreenOrientationChange();
	}

	/**
	 * 处理屏幕方向切换时的ui
	 */
	private void onScreenOrientationChange() {
		FragmentActivity appCompActivity = PlayerUtils.getAppCompActivity(getContext());
		ViewGroup.LayoutParams layoutParams = mContainerView.getLayoutParams();

		if (mCurrentScreenState == PlayerState.SCREEN_WINDOW_FULLSCREEN) {
			if (appCompActivity != null) {
				appCompActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}
			layoutParams.width = mScreenHeight;
			layoutParams.height = mScreenWidth;
			lockButton.setImageResource(R.drawable.player_icon_unlock);
			lockButton.setVisibility(VISIBLE);
			shareImageView.setVisibility(GONE);
		} else {
			if (appCompActivity != null) {
				appCompActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}
			layoutParams.width = mInitialWidth;
			layoutParams.height = mInitialHeight;
			lockButton.setVisibility(INVISIBLE);
			if (!mMediaBean.isShareIconHide()) {
				shareImageView.setVisibility(VISIBLE);
			}
		}
		mContainerView.setLayoutParams(layoutParams);

	}

	protected abstract void setMediaSource();

	/**
	 * 播放
	 */
	private void play() {
		mCurrentState = PlayerState.CURRENT_STATE_PREPARING;
		changeUiToPreparing();
		thumbImageView.setVisibility(VISIBLE);
		if (mAliyunPlayer != null) {
			mAliyunPlayer.stop();
		}
		setMediaSource();
		mAliyunPlayer.start();
	}

	/**
	 * 开始
	 */
	protected void start() {
		if (mAliyunPlayer != null) {
			mAliyunPlayer.start();
			if (mCurrentState == PlayerState.CURRENT_STATE_PAUSE) {
				mCurrentState = PlayerState.CURRENT_STATE_PLAYING;
				changeUiToPlayingShow();
			}
		}
	}

	/**
	 * 暂停
	 */
	protected void pause() {
		if (mAliyunPlayer != null) {
			mAliyunPlayer.pause();
			mCurrentState = PlayerState.CURRENT_STATE_PAUSE;
			changeUiToPauseShow();

			if (mOnPlayerStatusChangeListener != null) {
				mOnPlayerStatusChangeListener.onPause(mMediaBean.getCurrentPosition());
			}
		}
	}

	/**
	 * 停止
	 */
	protected void stop() {
		if (mAliyunPlayer != null) {
			mAliyunPlayer.stop();
			mCurrentState = PlayerState.CURRENT_STATE_STOP;
		}
	}

	@Override
	public void changeLockUi() {

		if (mCurrentScreenLockState == PlayerState.SCREEN_WINDOW_UNLOCK) {
			lockButton.setImageResource(R.drawable.player_icon_lock);
			mCurrentScreenLockState = PlayerState.SCREEN_WINDOW_LOCK;
			hideUiControls();
		} else if (mCurrentScreenLockState == PlayerState.SCREEN_WINDOW_LOCK) {
			mCurrentScreenLockState = PlayerState.SCREEN_WINDOW_UNLOCK;
			lockButton.setImageResource(R.drawable.player_icon_unlock);
			if (mCurrentState == PlayerState.CURRENT_STATE_PLAYING) {
				changeUiToPlayingShow();
			} else if (mCurrentState == PlayerState.CURRENT_STATE_PAUSE) {
				changeUiToPauseShow();
			} else if (mCurrentState == PlayerState.CURRENT_STATE_COMPLETE) {
				changeUiToComplete();
			} else if (mCurrentState == PlayerState.CURRENT_STATE_ERROR) {
				changeUiToError();
			}
		}
	}

	@Override
	public void onContextResume() {
		Log.i(TAG, "onContextResume");
		isContextBackground = false;
	}

	@Override
	public void onContextPause() {
		Log.i(TAG, "onContextPause");

	}

	@Override
	public void onContextStop() {
		Log.i(TAG, "onContextStop");
		isContextBackground = true;
		if (mCurrentState != PlayerState.CURRENT_STATE_ERROR && mAliyunPlayer != null) {
			//错误ui不需要改变
			mAliyunPlayer.pause();
		}
	}

	@Override
	public void onContextDestroy() {
		Log.i(TAG, "onContextDestroy");
		stop();
	}

	private void hideUi() {
		lockButton.setVisibility(INVISIBLE);
		hideUiControls();
	}

	public boolean isInterceptBackPressed() {
		if (mCurrentScreenState == PlayerState.SCREEN_WINDOW_FULLSCREEN) {
			fullscreenButton.performClick();
			mCurrentScreenLockState = PlayerState.SCREEN_WINDOW_UNLOCK;
			return true;
		}
		return false;
	}

	public void setContainerView(ViewGroup containerView) {
		this.mContainerView = containerView;
	}

	public void setOnPlayerStatusChangeListener(BasePlayerFragment.OnPlayerStatusChangeListener listener) {
		this.mOnPlayerStatusChangeListener = listener;
	}

	/**
	 * 隐藏UI的延时任务
	 */
	protected void startHideUiTimer() {
		cancelHideUiTimer();
		HIDE_CONTROL_VIEW_TIMER = new Timer();
		mHideUiTimerTask = new HideUiTimerTask();
		HIDE_CONTROL_VIEW_TIMER.schedule(mHideUiTimerTask, 3000);
	}


	private void cancelHideUiTimer() {
		if (HIDE_CONTROL_VIEW_TIMER != null) {
			HIDE_CONTROL_VIEW_TIMER.cancel();
		}
		if (mHideUiTimerTask != null) {
			mHideUiTimerTask.cancel();
		}
	}

	/**
	 * 3秒隐藏ui timer
	 */
	public class HideUiTimerTask extends TimerTask {

		@Override
		public void run() {
			FragmentActivity appCompActivity = PlayerUtils.getAppCompActivity(getContext());
			if (appCompActivity != null && !appCompActivity.isDestroyed() && mCurrentState == PlayerState.CURRENT_STATE_PLAYING && !isMoving) {
				post(new Runnable() {
					@Override
					public void run() {
						hideUi();
					}
				});
			}
		}
	}

}
