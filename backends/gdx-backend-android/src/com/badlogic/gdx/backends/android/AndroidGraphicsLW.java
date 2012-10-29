/*
 * Copyright 2010 Mario Zechner (contact@badlogicgames.com), Nathan Sweet (admin@esotericsoftware.com)
 * 
 * Modified by Elijah Cornell
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.badlogic.gdx.backends.android;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.EGLConfigChooser;
import android.opengl.GLSurfaceView.Renderer;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.android.surfaceview.DefaultGLSurfaceViewLW;
import com.badlogic.gdx.backends.android.surfaceview.GLBaseSurfaceViewLW;
import com.badlogic.gdx.backends.android.surfaceview.GLSurfaceView20LW;
import com.badlogic.gdx.backends.android.surfaceview.GdxEglConfigChooser;
import com.badlogic.gdx.backends.android.surfaceview.ResolutionStrategy;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.GL11;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GLCommon;
import com.badlogic.gdx.graphics.GLU;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.WindowedMean;

/**
 * An implementation of {@link Graphics} for Android.
 * 
 * @author mzechner
 */
public final class AndroidGraphicsLW extends AndroidGraphicsBase implements Graphics, Renderer {

	final GLBaseSurfaceViewLW view;

	AndroidApplicationLW app;

	public AndroidGraphicsLW(AndroidApplicationLW app,
			boolean useGL2IfAvailable, ResolutionStrategy resolutionStrategy) {

		view = createGLSurfaceView(app, useGL2IfAvailable, resolutionStrategy);
		this.app = app;

	}

	private GLBaseSurfaceViewLW createGLSurfaceView(AndroidApplicationLW app,
			boolean useGL2, ResolutionStrategy resolutionStrategy) {
		EGLConfigChooser configChooser = getEglConfigChooser();

		if (useGL2 && checkGL20()) {
			GLSurfaceView20LW view = new GLSurfaceView20LW(app.getEngine(),
					resolutionStrategy);
			if (configChooser != null)
				view.setEGLConfigChooser(configChooser);
			view.setRenderer(this);
			return view;
		} else {
			GLBaseSurfaceViewLW view = new DefaultGLSurfaceViewLW(app.getEngine(),
					resolutionStrategy);
			if (configChooser != null)
				view.setEGLConfigChooser(configChooser);

			view.setRenderer(this);
			return view;
		}
	}

	private EGLConfigChooser getEglConfigChooser() {
		if (!Build.DEVICE.equalsIgnoreCase("GT-I7500"))
			return null;
		else
			return new android.opengl.GLSurfaceView.EGLConfigChooser() {

				public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {

					// Ensure that we get a 16bit depth-buffer. Otherwise, we'll
					// fall
					// back to Pixelflinger on some device (read: Samsung I7500)
					int[] attributes = new int[] { EGL10.EGL_DEPTH_SIZE, 16,
							EGL10.EGL_NONE };
					EGLConfig[] configs = new EGLConfig[1];
					int[] result = new int[1];
					egl.eglChooseConfig(display, attributes, configs, 1, result);
					return configs[0];
				}
			};
	}

	private void updatePpi() {
		DisplayMetrics metrics = new DisplayMetrics();

		final Display display = ((WindowManager) app.getService()
				.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		display.getMetrics(metrics);

		ppiX = metrics.xdpi;
		ppiY = metrics.ydpi;
		ppcX = metrics.xdpi / 2.54f;
		ppcY = metrics.ydpi / 2.54f;
		density = metrics.density;
	}

	// private static boolean isPowerOfTwo(int value) {
	// return ((value != 0) && (value & (value - 1)) == 0);
	// }

	/**
	 * This instantiates the GL10, GL11 and GL20 instances. Includes the check
	 * for certain devices that pretend to support GL11 but fuck up vertex
	 * buffer objects. This includes the pixelflinger which segfaults when
	 * buffers are deleted as well as the Motorola CLIQ and the Samsung Behold
	 * II.
	 * 
	 * @param gl
	 */
	private void setupGL(javax.microedition.khronos.opengles.GL10 gl) {
		if (gl10 != null || gl20 != null)
			return;

		boolean isGL20 = checkGL20();
		Gdx.app.log("AndroidGraphics", "GL20: " + isGL20);

		if (view instanceof GLSurfaceView20LW) {
			gl20 = new AndroidGL20();
			this.gl = gl20;
		} else {
			gl10 = new AndroidGL10(gl);
			this.gl = gl10;
			if (gl instanceof javax.microedition.khronos.opengles.GL11) {
				String renderer = gl.glGetString(GL10.GL_RENDERER);
				if (!renderer.toLowerCase().contains("pixelflinger")
						&& !(android.os.Build.MODEL.equals("MB200")
								|| android.os.Build.MODEL.equals("MB220") || android.os.Build.MODEL
								.contains("Behold"))) {
					gl11 = new AndroidGL11(
							(javax.microedition.khronos.opengles.GL11) gl);
					gl10 = gl11;
				}
			}
		}
		
		this.glu = new AndroidGLU();

		Gdx.gl = this.gl;
		Gdx.gl10 = gl10;
		Gdx.gl11 = gl11;
		Gdx.gl20 = gl20;
		Gdx.glu = glu;

		Gdx.app.log("AndroidGraphics",
				"OGL renderer: " + gl.glGetString(GL10.GL_RENDERER));
		Gdx.app.log("AndroidGraphics",
				"OGL vendor: " + gl.glGetString(GL10.GL_VENDOR));
		Gdx.app.log("AndroidGraphics",
				"OGL version: " + gl.glGetString(GL10.GL_VERSION));
		Gdx.app.log("AndroidGraphics",
				"OGL extensions: " + gl.glGetString(GL10.GL_EXTENSIONS));
	}

	@Override
	public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,
			int width, int height) {
		this.width = width;
		this.height = height;
		updatePpi();
		gl.glViewport(0, 0, this.width, this.height);
		app.getListener().resize(width, height);
	}

	@Override
	public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,
			EGLConfig config) {
		setupGL(gl);
		logConfig(config);
		updatePpi();

		Mesh.invalidateAllMeshes(app);
		Texture.invalidateAllTextures(app);
		ShaderProgram.invalidateAllShaderPrograms(app);
		FrameBuffer.invalidateAllFrameBuffers(app);

		final Display display = ((WindowManager) app.getService()
				.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

		this.width = display.getWidth();
		this.height = display.getHeight();
		mean = new WindowedMean(5);
		this.lastFrameTime = System.nanoTime();

		gl.glViewport(0, 0, this.width, this.height);

		if (created == false) {
			app.getListener().create();
			created = true;
			synchronized (this) {
				running = true;
			}
		}
	}

	int[] value = new int[1];

	Object synch = new Object();

//	public void destroy() {
//		synchronized (synch) {
//			running = false;
//			destroy = true;
//
//			// TODO: Why was the wait here? Causes deadlock!?!
//			// while (destroy) {
//			// try {
//			// synch.wait();
//			// } catch (InterruptedException ex) {
//			// }
//			// }
//		}
//	}

	@Override
	public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {

		long time = System.nanoTime();
		deltaTime = (time - lastFrameTime) / 1000000000.0f;
		lastFrameTime = time;
		mean.addValue(deltaTime);

		boolean lrunning = false;
		boolean lpause = false;
		boolean ldestroy = false;
		boolean lresume = false;

		synchronized (synch) {
			lrunning = running;
			lpause = pause;
			ldestroy = destroy;
			lresume = resume;

			if (resume) {
				resume = false;
			}

			if (pause) {
				pause = false;
				synch.notifyAll();
			}

			if (destroy) {
				destroy = false;
				synch.notifyAll();
			}
		}

		if (lresume) {
			app.getListener().resume();
			Gdx.app.log("AndroidGraphics", "resumed");
		}

		// HACK: added null check to handle set wallpaper from preview null
		// error in renderer
		if (lrunning
				&& (Gdx.graphics.getGL10() != null
						|| Gdx.graphics.getGL11() != null || Gdx.graphics
						.getGL20() != null)) {
			
			synchronized(app.runnables) {
	      	   	for(int i = 0; i < app.runnables.size; i++) {
	      	   		app.runnables.get(i).run();
	      	   	}
	      	   	app.runnables.clear();
	      	}
			
			app.input.processEvents();
			app.getListener().render();
		}

		if (lpause) {
			app.getListener().pause();
	        ((AndroidAudio)app.getAudio()).pause();
			Gdx.app.log("AndroidGraphics", "paused");
		}

		if (ldestroy) {
			app.getListener().dispose();
			((AndroidAudio)app.getAudio()).dispose();
			Gdx.app.log("AndroidGraphics", "destroyed");
		}

		if (time - frameStart > 1000000000) {
			fps = frames;
			frames = 0;
			frameStart = time;
		}
		frames++;
	}

	protected void clearManagedCaches() {
		Mesh.clearAllMeshes(app);
		Texture.clearAllTextures(app);
		ShaderProgram.clearAllShaderPrograms(app);
		FrameBuffer.clearAllFrameBuffers(app);
		
	    Gdx.app.log("AndroidGraphics", Mesh.getManagedStatus());
	    Gdx.app.log("AndroidGraphics", Texture.getManagedStatus());
	    Gdx.app.log("AndroidGraphics", ShaderProgram.getManagedStatus());
	    Gdx.app.log("AndroidGraphics", FrameBuffer.getManagedStatus());
	}

	public GLBaseSurfaceViewLW getView() {
		return view;
	}

	@Override
	public DisplayMode[] getDisplayModes() {
		return new DisplayMode[0];
	}

	@Override
	public GLU getGLU() {
		return glu;
	}

    @Override
    public float getDensity()
    {
        return density;
    }

    @Override
    public DisplayMode getDesktopDisplayMode()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        
        final Display display = ((WindowManager) app.getService()
                .getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        display.getMetrics(metrics);

        return new AndroidDisplayMode(metrics.widthPixels, metrics.heightPixels, 0, 0);
    }


    
    @Override
    public boolean setDisplayMode(int width, int height, boolean fullscreen)
    {
        return false;
    }

    @Override
    public void setContinuousRendering(boolean isContinuous)
    {
        if(view != null) {
            this.isContinuous = isContinuous;
            int renderMode = isContinuous?GLSurfaceView.RENDERMODE_CONTINUOUSLY:GLSurfaceView.RENDERMODE_WHEN_DIRTY;
            view.setRenderMode(renderMode);
        }
    }

    @Override
    public void requestRendering()
    {
        if(view != null) {
            view.requestRender();
        }
        
    }
}