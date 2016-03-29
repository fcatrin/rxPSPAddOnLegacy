package org.ppsspp.ppsspp;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrobox.utils.GamepadInfoDialog;
import retrobox.utils.ImmersiveModeSetter;
import retrobox.utils.ListOption;
import retrobox.utils.RetroBoxDialog;
import retrobox.utils.RetroBoxUtils;
import retrobox.v2.ppsspp.R;
import retrobox.vinput.AnalogGamepad;
import retrobox.vinput.AnalogGamepad.Axis;
import retrobox.vinput.AnalogGamepadListener;
import retrobox.vinput.GenericGamepad;
import retrobox.vinput.GenericGamepad.Analog;
import retrobox.vinput.Mapper;
import retrobox.vinput.Mapper.ShortCut;
import retrobox.vinput.QuitHandler;
import retrobox.vinput.QuitHandler.QuitHandlerCallback;
import retrobox.vinput.VirtualEvent.MouseButton;
import retrobox.vinput.VirtualEventDispatcher;
import retrobox.vinput.overlay.GamepadController;
import retrobox.vinput.overlay.GamepadView;
import retrobox.vinput.overlay.Overlay;
import xtvapps.core.AndroidFonts;
import xtvapps.core.Callback;
import xtvapps.core.SimpleCallback;
import xtvapps.core.Utils;
import xtvapps.core.content.KeyValue;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

public class NativeActivity extends Activity {
	// Remember to loadLibrary your JNI .so in a static {} block

	// Adjust these as necessary
	private static String TAG = "NativeActivity";
	
	// Allows us to skip a lot of initialization on secondary calls to onCreate.
	private static boolean initialized = false;
	
	// Graphics and audio interfaces
	private NativeGLView mGLSurfaceView;
	protected NativeRenderer nativeRenderer;
	
	private String shortcutParam = "";
	
	public static String runCommand;
	public static String commandParameter;
	public static String installID;
	
	// Remember settings for best audio latency
	private int optimalFramesPerBuffer;
	private int optimalSampleRate;
	
	// audioFocusChangeListener to listen to changes in audio state
	private AudioFocusChangeListener audioFocusChangeListener;
	private AudioManager audioManager;
	
	private Vibrator vibrator;

	private boolean isXperiaPlay;
	private boolean shuttingDown;
    
    // Allow for multiple connected gamepads but just consider them the same for now.
    // Actually this is not entirely true, see the code.
    InputDeviceState inputPlayerA;
    InputDeviceState inputPlayerB;
    InputDeviceState inputPlayerC;
    String inputPlayerADesc;
    
    private GamepadInfoDialog gamepadInfoDialog;
    AnalogGamepad analogGamepad;
    public static final Overlay overlay = new Overlay();
	public static Mapper mapper;
	private VirtualInputDispatcher vinputDispatcher;
    private GamepadView gamepadView;
    private GamepadController gamepadController;
    private int saveSlot = 1;
    
    // Functions for the app activity to override to change behaviour.
    
    public boolean useLowProfileButtons() {
    	return true;
    }
    
	NativeRenderer getRenderer() {
		return nativeRenderer;
	}
    
	@TargetApi(17)
	private void detectOptimalAudioSettings() {
		try {
			optimalFramesPerBuffer = Integer.parseInt(this.audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
		} catch (NumberFormatException e) {
			// Ignore, if we can't parse it it's bogus and zero is a fine value (means we couldn't detect it).
		}
		try {
			optimalSampleRate = Integer.parseInt(this.audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
		} catch (NumberFormatException e) {
			// Ignore, if we can't parse it it's bogus and zero is a fine value (means we couldn't detect it).
		}
	}
	
	String getApplicationLibraryDir(ApplicationInfo application) {    
	    String libdir = null;
	    try {
	        // Starting from Android 2.3, nativeLibraryDir is available:
	        Field field = ApplicationInfo.class.getField("nativeLibraryDir");
	        libdir = (String) field.get(application);
	    } catch (SecurityException e1) {
	    } catch (NoSuchFieldException e1) {
	    } catch (IllegalArgumentException e) {
	    } catch (IllegalAccessException e) {
	    }
	    if (libdir == null) {
	        // Fallback for Android < 2.3:
	        libdir = application.dataDir + "/lib";
	    }
	    return libdir;
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	void GetScreenSizeJB(Point size, boolean real) {
        WindowManager w = getWindowManager();
		if (real) {
			w.getDefaultDisplay().getRealSize(size);
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	void GetScreenSizeHC(Point size, boolean real) {
        WindowManager w = getWindowManager();
		if (real && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			GetScreenSizeJB(size, real);
		} else {
			w.getDefaultDisplay().getSize(size);
		}
	}

	@SuppressWarnings("deprecation")
	public void GetScreenSize(Point size) {
        boolean real = useImmersive();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			GetScreenSizeHC(size, real);
		} else {
	        WindowManager w = getWindowManager();
	        Display d = w.getDefaultDisplay();
			size.x = d.getWidth();
			size.y = d.getHeight();
		}
	}
	
	public void setShortcutParam(String shortcutParam) {
		this.shortcutParam = ((shortcutParam == null) ? "" : shortcutParam);
	}
	
	public void Initialize() {
    	// Initialize audio classes. Do this here since detectOptimalAudioSettings()
		// needs audioManager
        this.audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		this.audioFocusChangeListener = new AudioFocusChangeListener();
		
        if (Build.VERSION.SDK_INT >= 17) {
        	// Get the optimal buffer sz
        	detectOptimalAudioSettings();
        }

        // isLandscape is used to trigger GetAppInfo currently, we 
        boolean landscape = NativeApp.isLandscape();
        Log.d(TAG, "Landscape: " + landscape);
        
    	// Get system information
		ApplicationInfo appInfo = null;  
		PackageManager packMgmr = getPackageManager();
		String packageName = getPackageName();
		try {
		    appInfo = packMgmr.getApplicationInfo(packageName, 0);
	    } catch  (NameNotFoundException e) {
		    e.printStackTrace();
		    throw new RuntimeException("Unable to locate assets, aborting...");
	    }

		int deviceType = NativeApp.DEVICE_TYPE_MOBILE;
		UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
		switch (uiModeManager.getCurrentModeType()) {
		case Configuration.UI_MODE_TYPE_TELEVISION:
			deviceType = NativeApp.DEVICE_TYPE_TV;
		    Log.i(TAG, "Running on an Android TV Device");
			break;
		case Configuration.UI_MODE_TYPE_DESK:
			deviceType = NativeApp.DEVICE_TYPE_DESKTOP;
		    Log.i(TAG, "Running on an Android desktop computer (!)");
			break;
		// All other device types are treated the same.
		}

	    isXperiaPlay = IsXperiaPlay();
		
		String libraryDir = getApplicationLibraryDir(appInfo);
	    File sdcard = Environment.getExternalStorageDirectory();

	    File externalStorageDir = new File(sdcard + "/PSPRBX");
	    externalStorageDir.mkdirs();
	    copyRetroBoxIniFile(externalStorageDir);
	    
	    String dataDir = this.getFilesDir().getAbsolutePath();
		String apkFilePath = appInfo.sourceDir; 

		// don't use built in specific controller handling
		String model = "RETRO BOX"; // Build.MANUFACTURER + ":" + Build.MODEL;
		String languageRegion = Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry(); 

		Point displaySize = new Point();
		GetScreenSize(displaySize);
		NativeApp.audioConfig(optimalFramesPerBuffer, optimalSampleRate);
		NativeApp.init(model, deviceType, displaySize.x, displaySize.y, languageRegion, apkFilePath, dataDir, externalStorageDir.getAbsolutePath(), libraryDir, shortcutParam, installID, Build.VERSION.SDK_INT);

		NativeApp.sendMessage("cacheDir", getCacheDir().getAbsolutePath());

		// OK, config should be initialized, we can query for screen rotation.
		if (Build.VERSION.SDK_INT >= 9) {
			updateScreenRotation();
		}	

	    // Detect OpenGL support.
	    // We don't currently use this detection for anything but good to have in the log.
        if (!detectOpenGLES20()) {
        	Log.i(TAG, "OpenGL ES 2.0 NOT detected. Things will likely go badly.");
        } else {
        	if (detectOpenGLES30()) {
            	Log.i(TAG, "OpenGL ES 3.0 detected.");
        	}
        	else {
            	Log.i(TAG, "OpenGL ES 2.0 detected.");
        	}
        }
        
        vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= 11) {
        	checkForVibrator();
        }
	}
	
	private void copyRetroBoxIniFile(File externalStorageDir) {
		try {
			File iniFile = new File(externalStorageDir, "/PSP/SYSTEM/ppsspp.ini");
			iniFile.getParentFile().mkdirs();
			Utils.copyFile(new File(getIntent().getStringExtra("iniFile")), iniFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@TargetApi(9)
	private void updateScreenRotation() {
		// Query the native application on the desired rotation.
		int rot = 0;
		String rotString = NativeApp.queryConfig("screenRotation");
		try {
			rot = Integer.parseInt(rotString);
		} catch (NumberFormatException e) {
			Log.e(TAG, "Invalid rotation: " + rotString);
			return;
		}
		switch (rot) {
		case 0:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			break;
		case 1:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			break;
		case 2:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			break;
		case 3:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
			break;
		case 4:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
			break;
		}
	}
	
	private boolean useImmersive() {
		String immersive = NativeApp.queryConfig("immersiveMode");
		return immersive.equals("1") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
	}

	@SuppressLint("InlinedApi")
	@TargetApi(14)
	private void updateSystemUiVisibility() {
		int flags = 0;
		if (useLowProfileButtons()) {
			flags |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
		}
		if (useImmersive()) {
			flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
		}
		if (getWindow().getDecorView() != null) {
			getWindow().getDecorView().setSystemUiVisibility(flags);
		} else {
			Log.e(TAG, "updateSystemUiVisibility: decor view not yet created, ignoring");
		}
	}
	
	// Need API 11 to check for existence of a vibrator? Zany.
	@TargetApi(11)
	public void checkForVibrator() {
        if (Build.VERSION.SDK_INT >= 11) {
	        if (!vibrator.hasVibrator()) {
	        	vibrator = null;
	        }
        }
	}

	// Override this to scale the backbuffer (use the Android hardware scaler)
	public void getDesiredBackbufferSize(Point sz) {
		sz.x = 0;
		sz.y = 0;
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		shuttingDown = false;
    	installID = Installation.id(this);

		if (!initialized) {
			Initialize();
			initialized = true;
		}
		// Keep the screen bright - very annoying if it goes dark when tilting away
		Window window = this.getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		gainAudioFocus(this.audioManager, this.audioFocusChangeListener);
        NativeApp.audioInit();
        
        mGLSurfaceView = new NativeGLView(this);
		nativeRenderer = new NativeRenderer(this);

		Point sz = new Point();
		getDesiredBackbufferSize(sz);
		if (sz.x > 0) {
			Log.i(TAG, "Requesting fixed size buffer: " + sz.x + "x" + sz.y);
			// Auto-calculates new DPI and forwards to the correct call on mGLSurfaceView.getHolder()
			nativeRenderer.setFixedSize(sz.x, sz.y, mGLSurfaceView);
		}
        mGLSurfaceView.setEGLContextClientVersion(2);
        
        // Setup the GLSurface and ask android for the correct 
        // Number of bits for r, g, b, a, depth and stencil components
        // The PSP only has 16-bit Z so that should be enough.
        // Might want to change this for other apps (24-bit might be useful).
        // Actually, we might be able to do without both stencil and depth in
        // the back buffer, but that would kill non-buffered rendering.
        
        // It appears some gingerbread devices blow up if you use a config chooser at all ????  (Xperia Play)
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        
        // On some (especially older devices), things blow up later (EGL_BAD_MATCH) if we don't set the format here,
        // if we specify that we want destination alpha in the config chooser, which we do.
        // http://grokbase.com/t/gg/android-developers/11bj40jm4w/fall-back
        
        
        // Needed to avoid banding on Ouya?
        if (Build.MANUFACTURER == "OUYA") {
        	mGLSurfaceView.getHolder().setFormat(PixelFormat.RGBX_8888);
        	mGLSurfaceView.setEGLConfigChooser(new NativeEGLConfigChooser());
        }
        
        mGLSurfaceView.setRenderer(nativeRenderer);
        setRetroBoxTVContentView(mGLSurfaceView);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			updateSystemUiVisibility();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				setupSystemUiCallback();
			}
		}
    }
    
    private void setRetroBoxTVContentView(NativeGLView surface) {
		setContentView(R.layout.root);
        ViewGroup root = (ViewGroup)findViewById(R.id.root);
        root.addView(surface, 0);
        
AndroidFonts.setViewFont(findViewById(R.id.txtDialogListTitle), RetroBoxUtils.FONT_DEFAULT_M);
        
        AndroidFonts.setViewFont(findViewById(R.id.txtGamepadInfoTop), RetroBoxUtils.FONT_DEFAULT_M);
        AndroidFonts.setViewFont(findViewById(R.id.txtGamepadInfoBottom), RetroBoxUtils.FONT_DEFAULT_M);

        gamepadInfoDialog = new GamepadInfoDialog(this);
        gamepadInfoDialog.loadFromIntent(getIntent());

    	gamepadController = new GamepadController();
    	vinputDispatcher = new VirtualInputDispatcher();

        mapper = new Mapper(getIntent(), vinputDispatcher);
        Mapper.initGestureDetector(this);
        gamepadView = new GamepadView(this, overlay);
        
        for(int i=0; i<4; i++) {
        	String prefix = "j" + (i+1);
        	String deviceDescriptor = getIntent().getStringExtra(prefix + "DESCRIPTOR");
    		Mapper.registerGamepad(i, deviceDescriptor);
        }
        
    	setupGamepadOverlay(root);
    	analogGamepad = new AnalogGamepad(0, 0, new AnalogGamepadListener() {
			
			@Override
			public void onMouseMoveRelative(float mousex, float mousey) {}
			
			@Override
			public void onMouseMove(int mousex, int mousey) {}
			
			@Override
			public void onAxisChange(GenericGamepad gamepad, float axisx, float axisy, float hatx, float haty) {
				vinputDispatcher.sendAnalog(gamepad, Analog.LEFT, axisx, -axisy, hatx, haty);
			}

			@Override
			public void onDigitalX(GenericGamepad gamepad, Axis axis, boolean on) {}

			@Override
			public void onDigitalY(GenericGamepad gamepad, Axis axis, boolean on) {}
			
			@Override
			public void onTriggers(String deviceDescriptor, int deviceId, boolean left, boolean right) {
				mapper.handleTriggerEvent(deviceDescriptor, deviceId, left, right); 
			}

		});

    }
    
    private void setImmersiveMode() {
    	ImmersiveModeSetter.get().setImmersiveMode(getWindow(), isStableLayout());
	}
    
	private boolean isStableLayout() {
		return Mapper.hasGamepads();
	}

    @TargetApi(19)
	void setupSystemUiCallback() {
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if (visibility == 0) {
                	updateSystemUiVisibility();
                }
            }
        });
    }
    
    @Override
    protected void onStop() {
    	super.onStop(); 
    	Log.i(TAG, "onStop - do nothing special");
    } 

    @Override
	protected void onDestroy() {
		super.onDestroy();
      	Log.i(TAG, "onDestroy");
		mGLSurfaceView.onDestroy();
		nativeRenderer.onDestroyed();
		NativeApp.audioShutdown();
		// Probably vain attempt to help the garbage collector...
		mGLSurfaceView = null;
		audioFocusChangeListener = null;
		audioManager = null;
		if (shuttingDown) {
            NativeApp.shutdown();
		}
	}  
	
    private boolean detectOpenGLES20() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo info = am.getDeviceConfigurationInfo();
        return info.reqGlEsVersion >= 0x20000;
    }

    private boolean detectOpenGLES30() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo info = am.getDeviceConfigurationInfo();
        return info.reqGlEsVersion >= 0x30000;
    }
   
    @Override 
    protected void onPause() {
        super.onPause();
    	Log.i(TAG, "onPause");
    	loseAudioFocus(this.audioManager, this.audioFocusChangeListener);
        NativeApp.pause();
        mGLSurfaceView.onPause();
    }
      
	@Override
	protected void onResume() {
		super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            updateSystemUiVisibility();
        }
		// OK, config should be initialized, we can query for screen rotation.
		if (Build.VERSION.SDK_INT >= 9) {
			updateScreenRotation();
		}	

		Log.i(TAG, "onResume");
		if (mGLSurfaceView != null) {
			mGLSurfaceView.onResume();
		} else {
			Log.e(TAG, "mGLSurfaceView really shouldn't be null in onResume");
		}
		
		gainAudioFocus(this.audioManager, this.audioFocusChangeListener);
		NativeApp.resume();
	}
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    	super.onConfigurationChanged(newConfig);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            updateSystemUiVisibility();
        }
        
		Point sz = new Point();
		getDesiredBackbufferSize(sz);
		if (sz.x > 0) {
			mGLSurfaceView.getHolder().setFixedSize(sz.x/2, sz.y/2);
		}
    }

	//keep this static so we can call this even if we don't
	//instantiate NativeAudioPlayer
	public static void gainAudioFocus(AudioManager audioManager, AudioFocusChangeListener focusChangeListener) {
		if (audioManager != null) {
			audioManager.requestAudioFocus(focusChangeListener,
					AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
		}
	}
	
	//keep this static so we can call this even if we don't
	//instantiate NativeAudioPlayer
	public static void loseAudioFocus(AudioManager audioManager,AudioFocusChangeListener focusChangeListener){
		if (audioManager != null) {
			audioManager.abandonAudioFocus(focusChangeListener);
		}
	}
	
    // We simply grab the first input device to produce an event and ignore all others that are connected.
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private InputDeviceState getInputDeviceState(InputEvent event) {
        InputDevice device = event.getDevice();
        if (device == null) {
            return null;
        }
        if (inputPlayerA == null) {
            inputPlayerADesc = getInputDesc(device);
            Log.i(TAG, "Input player A registered: desc = " + inputPlayerADesc);
            inputPlayerA = new InputDeviceState(device);
        }

        if (inputPlayerA.getDevice() == device) {
            return inputPlayerA;
        }

        if (inputPlayerB == null) {
            Log.i(TAG, "Input player B registered: desc = " + getInputDesc(device));
            inputPlayerB = new InputDeviceState(device);
        }

        if (inputPlayerB.getDevice() == device) {
            return inputPlayerB;
        }

        if (inputPlayerC == null) {
            Log.i(TAG, "Input player C registered");
            inputPlayerC = new InputDeviceState(device);
        }

        if (inputPlayerC.getDevice() == device) {
            return inputPlayerC;
        }

        return inputPlayerA;
    }

    public boolean IsXperiaPlay() {
        return android.os.Build.MODEL.equals("R800a") || android.os.Build.MODEL.equals("R800i") || android.os.Build.MODEL.equals("R800x") || android.os.Build.MODEL.equals("R800at") || android.os.Build.MODEL.equals("SO-01D") || android.os.Build.MODEL.equals("zeus");
    }

    // We grab the keys before onKeyDown/... even see them. This is also better because it lets us
    // distinguish devices.
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean keyDown = event.getAction() == KeyEvent.ACTION_DOWN;
        int keyCode = event.getKeyCode();
    	if (RetroBoxDialog.isDialogVisible(this)) {
    		if (keyDown) {
    			if (RetroBoxDialog.onKeyDown(this, keyCode, event)) return true;
    		} else {
    			if (RetroBoxDialog.onKeyUp(this, keyCode, event)) return true;
    		}
    		return super.dispatchKeyEvent(event);
    	}
    	if (mapper.handleKeyEvent(event, keyCode, keyDown)) return true;
    	
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1 && !isXperiaPlay) {
			InputDeviceState state = getInputDeviceState(event);
			if (state == null) {
				return super.dispatchKeyEvent(event);
			}

			// Let's let back and menu through to dispatchKeyEvent.
			boolean passThrough = false;

			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_MENU:
				passThrough = true;
				break;
			default:
				break;
			}

			// Don't passthrough back button if gamepad.
			int sources = event.getSource();
			switch (sources) {
			case InputDevice.SOURCE_GAMEPAD:
			case InputDevice.SOURCE_JOYSTICK:
			case InputDevice.SOURCE_DPAD:
				passThrough = false;
				break;
			}

			if (!passThrough) {
				switch (event.getAction()) {
				case KeyEvent.ACTION_DOWN:
					if (state.onKeyDown(event)) {
						return true;
					}
					break;

				case KeyEvent.ACTION_UP:
					if (state.onKeyUp(event)) {
						return true;
					}
					break;
				}
			}
        }
        
        // Let's go through the old path (onKeyUp, onKeyDown).
		return super.dispatchKeyEvent(event);
    } 
    
	@TargetApi(16)
	static public String getInputDesc(InputDevice input) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			return input.getDescriptor();
		} else {
			List<InputDevice.MotionRange> motions = input.getMotionRanges();
			String fakeid = "";
			for (InputDevice.MotionRange range : motions)
				fakeid += range.getAxis();
			return fakeid;
		}
	}

	@Override
	@TargetApi(12)
	public boolean onGenericMotionEvent(MotionEvent event) {
    	if (RetroBoxDialog.isDialogVisible(this)) {
    		return super.onGenericMotionEvent(event);
    	}
    	
		if (analogGamepad != null && analogGamepad.onGenericMotionEvent(event)) return true;
		
		// Log.d(TAG, "onGenericMotionEvent: " + event);
		if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != 0) {
	        if (Build.VERSION.SDK_INT >= 12) {
	        	InputDeviceState state = getInputDeviceState(event);
	        	if (state == null) {
	        		Log.w(TAG, "Joystick event but failed to get input device state.");
	        		return super.onGenericMotionEvent(event);
	        	}
	        	state.onJoystickMotion(event);
	        	return true;
	        }
		}

		if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
	         switch (event.getAction()) {
	             case MotionEvent.ACTION_HOVER_MOVE:
	                 // process the mouse hover movement...
	                 return true;
	             case MotionEvent.ACTION_SCROLL:
	                 NativeApp.mouseWheelEvent(event.getX(), event.getY());
	                 return true;
	         }
	    }
		return super.onGenericMotionEvent(event);
	}

	@SuppressLint("NewApi")
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// Eat these keys, to avoid accidental exits / other screwups.
		// Maybe there's even more we need to eat on tablets?
		boolean repeat = event.getRepeatCount() > 0;
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			/*
			if (event.isAltPressed()) {
				NativeApp.keyDown(0, 1004, repeat); // special custom keycode for the O button on Xperia Play
			} else if (NativeApp.isAtTopLevel()) {
				Log.i(TAG, "IsAtTopLevel returned true.");
				// Pass through the back event.
				return super.onKeyDown(keyCode, event);
			} else {
				NativeApp.keyDown(0, keyCode, repeat);
			}
			*/
			return true;
		case KeyEvent.KEYCODE_MENU:
		case KeyEvent.KEYCODE_SEARCH:
			/*
			NativeApp.keyDown(0, keyCode, repeat);
			*/
			return true;
			
		case KeyEvent.KEYCODE_DPAD_UP:
		case KeyEvent.KEYCODE_DPAD_DOWN:
		case KeyEvent.KEYCODE_DPAD_LEFT:
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			// Joysticks are supported in Honeycomb MR1 and later via the onGenericMotionEvent method.
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1 && event.getSource() == InputDevice.SOURCE_JOYSTICK) {
				return super.onKeyDown(keyCode, event);
			}
			// Fall through
		default:
			// send the rest of the keys through.
			// TODO: get rid of the three special cases above by adjusting the native side of the code.
			// Log.d(TAG, "Key down: " + keyCode + ", KeyEvent: " + event);
			return NativeApp.keyDown(0, keyCode, repeat);
		}
	}

	@SuppressLint("NewApi")
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			/*
			if (event.isAltPressed()) {
				NativeApp.keyUp(0, 1004); // special custom keycode
			} else if (NativeApp.isAtTopLevel()) {
				Log.i(TAG, "IsAtTopLevel returned true.");
				return super.onKeyUp(keyCode, event);
			} else {
				NativeApp.keyUp(0, keyCode);
			}
			return true;
			*/
		case KeyEvent.KEYCODE_MENU:
		case KeyEvent.KEYCODE_SEARCH:
			// Search probably should also be ignored. We send it to the app.
			// NativeApp.keyUp(0, keyCode);
			openRetroBoxMenu();
			return true;

		case KeyEvent.KEYCODE_DPAD_UP:
		case KeyEvent.KEYCODE_DPAD_DOWN:
		case KeyEvent.KEYCODE_DPAD_LEFT:
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			// Joysticks are supported in Honeycomb MR1 and later via the onGenericMotionEvent method.
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1 && event.getSource() == InputDevice.SOURCE_JOYSTICK) {
				return super.onKeyUp(keyCode, event);
			}
			// Fall through
		default:
			// send the rest of the keys through.
			// TODO: get rid of the three special cases above by adjusting the native side of the code.
			// Log.d(TAG, "Key down: " + keyCode + ", KeyEvent: " + event);
			return NativeApp.keyUp(0, keyCode);
		}
	}

	
	@TargetApi(11)
	private AlertDialog.Builder createDialogBuilderWithTheme() {
   		return new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK);
	}
	
	// The return value is sent elsewhere. TODO in java, in SendMessage in C++.
	public void inputBox(String title, String defaultText, String defaultAction) {
    	final FrameLayout fl = new FrameLayout(this);
    	final EditText input = new EditText(this);
    	input.setGravity(Gravity.CENTER);

    	FrameLayout.LayoutParams editBoxLayout = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
    	editBoxLayout.setMargins(2, 20, 2, 20);
    	fl.addView(input, editBoxLayout);

    	input.setInputType(InputType.TYPE_CLASS_TEXT);
    	input.setText(defaultText);
    	input.selectAll();
    	
    	AlertDialog.Builder bld = null;
    	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
    		bld = new AlertDialog.Builder(this);
    	else
    		bld = createDialogBuilderWithTheme();

    	AlertDialog dlg = bld
    		.setView(fl)
    		.setTitle(title)
    		.setPositiveButton(defaultAction, new DialogInterface.OnClickListener(){
    			public void onClick(DialogInterface d, int which) {
    	    		NativeApp.sendMessage("inputbox_completed", input.getText().toString());
    				d.dismiss();
    			}
    		})
    		.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
    			public void onClick(DialogInterface d, int which) {
    	    		NativeApp.sendMessage("inputbox_failed", "");
    				d.cancel();
    			}
    		}).create();
    	
    	dlg.setCancelable(true);
    	dlg.show();
    }
	
    public boolean processCommand(String command, String params) {
		if (command.equals("launchBrowser")) {
			try {
				Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(params));
				startActivity(i);
				return true;
			} catch (Exception e) {
				// No browser?
				Log.e(TAG, e.toString());
				return false;
			}
		} else if (command.equals("launchEmail")) {
			try {
				Intent send = new Intent(Intent.ACTION_SENDTO);
				String uriText;
				uriText = "mailto:email@gmail.com" + "?subject=Your app is..."
						+ "&body=great! Or?";
				uriText = uriText.replace(" ", "%20");
				Uri uri = Uri.parse(uriText);
				send.setData(uri);
				startActivity(Intent.createChooser(send, "E-mail the app author!"));
				return true;
			} catch (Exception e) {  // For example, android.content.ActivityNotFoundException
				Log.e(TAG, e.toString());
				return false;
			}
		} else if (command.equals("sharejpeg")) {
			try {
				Intent share = new Intent(Intent.ACTION_SEND);
				share.setType("image/jpeg");
				share.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + params));
				startActivity(Intent.createChooser(share, "Share Picture"));
				return true;
			} catch (Exception e) {  // For example, android.content.ActivityNotFoundException
				Log.e(TAG, e.toString());
				return false;
			}
		} else if (command.equals("sharetext")) {
			try {
				Intent sendIntent = new Intent();
				sendIntent.setType("text/plain");
				sendIntent.putExtra(Intent.EXTRA_TEXT, params);
				sendIntent.setAction(Intent.ACTION_SEND);
				startActivity(sendIntent);
				return true;
			} catch (Exception e) {  // For example, android.content.ActivityNotFoundException
				Log.e(TAG, e.toString());
				return false;
			}
		} else if (command.equals("showTwitter")) {
			try {
				String twitter_user_name = params;
				try {
					startActivity(new Intent(Intent.ACTION_VIEW,
							Uri.parse("twitter://user?screen_name="
									+ twitter_user_name)));
				} catch (Exception e) {
					startActivity(new Intent(
							Intent.ACTION_VIEW,
							Uri.parse("https://twitter.com/#!/" + twitter_user_name)));
				}
				return true;
			} catch (Exception e) {  // For example, android.content.ActivityNotFoundException
				Log.e(TAG, e.toString());
				return false;
			}
		} else if (command.equals("launchMarket")) {
			// Don't need this, can just use launchBrowser with a market:
			// http://stackoverflow.com/questions/3442366/android-link-to-market-from-inside-another-app
			// http://developer.android.com/guide/publishing/publishing.html#marketintent
			return false;
		} else if (command.equals("toast")) {
			Toast toast = Toast.makeText(this, params, Toast.LENGTH_SHORT);
			toast.show();
			Log.i(TAG, params);
			return true;
		} else if (command.equals("showKeyboard")) {
			InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			// No idea what the point of the ApplicationWindowToken is or if it
			// matters where we get it from...
			inputMethodManager.toggleSoftInputFromWindow(
					mGLSurfaceView.getApplicationWindowToken(),
					InputMethodManager.SHOW_FORCED, 0);
			return true;
		} else if (command.equals("hideKeyboard")) {
			InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			inputMethodManager.toggleSoftInputFromWindow(
					mGLSurfaceView.getApplicationWindowToken(),
					InputMethodManager.SHOW_FORCED, 0);
			return true;
		} else if (command.equals("inputbox")) {
			String title = "Input";
			String defString = "";
			String[] param = params.split(":");
			if (param[0].length() > 0)
				title = param[0];
			if (param.length > 1)
				defString = param[1];
			Log.i(TAG, "Launching inputbox: " + title + " " + defString);
			inputBox(title, defString, "OK");
			return true;
		} else if (command.equals("vibrate")) {
			int milliseconds = -1;
			if (params != "") {
				try {
					milliseconds = Integer.parseInt(params);
				} catch (NumberFormatException e) {
				}
			}
			// Special parameters to perform standard haptic feedback
			// operations
			// -1 = Standard keyboard press feedback
			// -2 = Virtual key press
			// -3 = Long press feedback
			// Note that these three do not require the VIBRATE Android
			// permission.
			switch (milliseconds) {
			case -1:
				mGLSurfaceView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
				break;
			case -2:
				mGLSurfaceView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
				break;
			case -3:
				mGLSurfaceView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
				break;
			default:
				if (vibrator != null) {
					vibrator.vibrate(milliseconds);
				}
				break;
			}
			return true;
		} else if (command.equals("finish")) {
			shuttingDown = true;
			finish();
		} else if (command.equals("rotate")) {
			if (Build.VERSION.SDK_INT >= 9) {
				updateScreenRotation();
			}	
		} else if (command.equals("immersive")) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				updateSystemUiVisibility();
			}
		} else if (command.equals("recreate")) {
			recreate();
		}
    	return false;
    }

    @SuppressLint("NewApi")
	@Override
    public void recreate()
    {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
        {
            super.recreate();
        }
        else
        {
            startActivity(getIntent());
            finish();
        }
    }
    
    private void uiChangeSlot() {
   		List<ListOption> options = new ArrayList<ListOption>();
    	options.add(new ListOption("", "Cancel"));
    	for(int i=1; i<=5; i++) {
    		options.add(new ListOption(i+"", "Use save slot " + i, (i==saveSlot)?"Active":""));
    	}
    	
    	RetroBoxDialog.showListDialog(this, "RetroBoxTV", options, new Callback<KeyValue>() {
			@Override
			public void onResult(KeyValue result) {
				int slot = Utils.str2i(result.getKey());
				if (slot>0 && slot!=saveSlot) {
					saveSlot = slot;
					NativeApp.setStateSlot(saveSlot - 1);
					toastMessage("Save State slot changed to " + slot);
				}
				openRetroBoxMenu(false);
			}

			@Override
			public void onError() {
				openRetroBoxMenu(false);
			}
			
    	});
    }
    
    private void openRetroBoxMenu() {
    	openRetroBoxMenu(true);
    }
    
    private void openRetroBoxMenu(boolean pause) {
    	if (pause) onPauseFast();
    	
    	List<ListOption> options = new ArrayList<ListOption>();
    	options.add(new ListOption("", "Cancel"));
    	options.add(new ListOption("load", "Load State"));
    	options.add(new ListOption("save", "Save State"));
    	options.add(new ListOption("slot", "Change Save State slot", "Slot " + saveSlot));
    	options.add(new ListOption("help", "Help"));
    	options.add(new ListOption("quit", "Quit"));
    	
    	
    	RetroBoxDialog.showListDialog(this, "RetroBoxTV", options, new Callback<KeyValue>() {
			@Override
			public void onResult(KeyValue result) {
				String key = result.getKey();
				if (key.equals("load")) {
					uiLoadState();
				} else if (key.equals("save")) {
					uiSaveState();
				} else if (key.equals("quit")) {
					uiQuit();
				} else if (key.equals("slot")) {
					uiChangeSlot();
					return;
				} else if (key.equals("help")) {
					uiHelp();
					return;
				}
				onResumeFast();
			}

			@Override
			public void onError() {
				onResumeFast();
			}
		});
    	
    }
    
    protected void onPauseFast() {
    	NativeApp.sendMessage("pause", "");
    }
    
    protected void onResumeFast() {
    	sendKeyPress(KeyEvent.KEYCODE_BACK);
    }
    
    private void sendKeyPress(final int keyCode) {
    	final int deviceId = NativeApp.DEVICE_ID_PAD_0;
		NativeApp.keyDown(deviceId, keyCode, false);
		new Handler().postDelayed(new Runnable() {

			@Override
			public void run() {
				NativeApp.keyUp(deviceId, keyCode);
			}
		}, 100);
    }
    
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (RetroBoxDialog.isDialogVisible(this)) {
			return super.dispatchTouchEvent(ev);
		}
		
		if (gamepadView.isVisible() && gamepadController.onTouchEvent(ev)) {
			if (Overlay.requiresRedraw) {
				Overlay.requiresRedraw = false;
				gamepadView.invalidate();
			}
			return true;
		}
		
		mapper.onTouchEvent(ev);
		
		return super.dispatchTouchEvent(ev);
	}
	
	private int last_w = 0;
	private int last_h = 0;
	
	private void setupGamepadOverlay(final ViewGroup root) {
		ViewTreeObserver observer = root.getViewTreeObserver();
		observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				int w = root.getWidth();
				int h = root.getHeight();
				if (w == last_w || h == last_h) return;
				last_w = w;
				last_h = h;
				
				Log.d("OVERLAY", "set dimensions " + w + "x" + h);
				// mLifecycleHandler.updateScreenSize(w, h);
				if (needsOverlay()) {
					String overlayConfig = getIntent().getStringExtra("OVERLAY");
					float alpha = getIntent().getFloatExtra("OVERLAY_ALPHA", 0.8f);
					if (overlayConfig!=null) overlay.init(overlayConfig, w, h, alpha);
				}
				}
			});
		
		Log.d("OVERLAY", "setupGamepadOverlay");
		if (needsOverlay()) {
			Log.d("OVERLAY", "has Overlay");
			gamepadView.addToLayout(root);
			gamepadView.showPanel();
		}
	}
	
	private boolean needsOverlay() {
		return !Mapper.hasGamepads();
	}

    @Override
	public void onBackPressed() {
    	if (RetroBoxDialog.cancelDialog(this)) return;
    	
    	openRetroBoxMenu();
	}

    private void toastMessage(final String message) {
    	Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    private void uiLoadState() {
    	NativeApp.loadState();
    	toastMessage("State was restored");
    }

    private void uiSaveState() {
    	NativeApp.saveState();
    	toastMessage("State was saved");
    }
    
    protected void uiHelp() {
		RetroBoxDialog.showGamepadDialogIngame(this, gamepadInfoDialog, new SimpleCallback() {
			@Override
			public void onResult() {
				onResumeFast();
			}
		});
    }

	private void uiQuit() {
		processCommand("finish", null);
	}
	
    protected void uiQuitConfirm() {
    	QuitHandler.askForQuit(this, new QuitHandlerCallback() {
			@Override
			public void onQuit() {
				uiQuit();
			}
		});
    }
    
	class VirtualInputDispatcher implements VirtualEventDispatcher {

		@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
		@Override
		public void sendKey(GenericGamepad gamepad, int keyCode, boolean down) {
			// emulate joystick events
			
			float axisX = 0;
			float axisY = 0;
			switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_LEFT: axisX = down?-1:0; break;
			case KeyEvent.KEYCODE_DPAD_RIGHT: axisX = down?1:0; break;
			case KeyEvent.KEYCODE_DPAD_UP: axisY = down?-1:0; break;
			case KeyEvent.KEYCODE_DPAD_DOWN: axisY = down?1:0; break;
			}
			
			int deviceId = NativeApp.DEVICE_ID_PAD_0;
			NativeApp.beginJoystickEvent();
			NativeApp.joystickAxis(deviceId, MotionEvent.AXIS_HAT_X, axisX);
			NativeApp.joystickAxis(deviceId, MotionEvent.AXIS_HAT_Y, axisY);
			NativeApp.endJoystickEvent();
			
			if (down) NativeApp.keyDown(deviceId, keyCode, false);
			else NativeApp.keyUp(deviceId, keyCode);
		}

		@Override
		public void sendMouseButton(MouseButton button, boolean down) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public boolean handleShortcut(ShortCut shortcut, boolean down) {
			switch(shortcut) {
			case EXIT: if (!down) uiQuitConfirm(); return true;
			case LOAD_STATE: if (!down) uiLoadState(); return true;
			case SAVE_STATE: if (!down) uiSaveState(); return true;
			case MENU : if (!down) openRetroBoxMenu(); return true;
			default:
				return false;
			}
		}

		@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
		@Override
		public void sendAnalog(GenericGamepad gamepad, Analog index, double x,
				double y, double hatx, double haty) {
			Log.d("sendAnalog", "index " + index + " " + x + ", " + y + "   hatx:" + hatx + " haty:" + haty);
			int deviceId = NativeApp.DEVICE_ID_PAD_0;
			NativeApp.beginJoystickEvent();
			NativeApp.joystickAxis(deviceId, MotionEvent.AXIS_HAT_X, (float)hatx);
			NativeApp.joystickAxis(deviceId, MotionEvent.AXIS_HAT_Y, (float)haty);
			NativeApp.endJoystickEvent();
		}
	}

}