/*
 * Copyright (C) 2013 poster PCE YoungSee Inc.
 * All Rights Reserved Proprietary and Confidential.
 * 
 * @author LiLiang-Ping
 */

package com.youngsee.dual.screenmanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Time;

import com.youngsee.dual.authorization.AuthorizationActivity;
import com.youngsee.dual.authorization.AuthorizationManager;
import com.youngsee.dual.common.FileUtils;
import com.youngsee.dual.common.Md5;
import com.youngsee.dual.common.MediaInfoRef;
import com.youngsee.dual.common.SubWindowInfoRef;
import com.youngsee.dual.ftpoperation.FtpFileInfo;
import com.youngsee.dual.ftpoperation.FtpHelper;
import com.youngsee.dual.logmanager.Logger;
import com.youngsee.dual.posterdisplayer.PosterApplication;
import com.youngsee.dual.posterdisplayer.PosterMainActivity;
import com.youngsee.dual.webservices.XmlParser;

@SuppressLint("DefaultLocale")
public class ScreenManager
{
    private static final int     IDLE_STATE                = 0;
    private static final int     PLAYING_NORMAL_PROGRAM    = 1;
    private static final int     PLAYING_EMERGENCE_PROGRAM = 2;
    private static final int     NO_STORAGE_STATE          = 3;
    
    private static final int     NORMAL_PROGRAM            = 1;
    private static final int     ERGENT_PROGRAM            = 2;
    
    public static final int     NO_OPERATE             = 0;
    public static final int     CLEAR_PGM_OPERATE      = 1;
    public static final int     UPDATE_PGM_OPERATE     = 2;
    
    private Context              mContext                  = null;
    private ScreenDaemon         mScreenDaemonThread       = null;
    private static ScreenManager mScreenManagerInstance    = null;
    
    public boolean               mOsdIsOpen                = false;
    public boolean               mNormalPgmFileHasChanged  = false;
    public boolean               mUrgentPgmFileHasChanged  = false;
    public Object                mProgramFileLock          = new Object();
    
    private boolean              mLoadProgramDone          = false;
    private boolean              mStandbyScreenIsShow      = false;
    
    // Define message Id
    private static final int     EVENT_SHOW_IDLE_PROGRAM   = 0x8001;
    private final static int     EVENT_SHOW_NORMAL_PROGRAM = 0x8002;
    private final static int     EVENT_SHOW_URGENT_PROGRAM = 0x8003;

    // 节目信息
    private final class ProgramInfo
    {
        public String       startDate     = null;
        public String       endDate       = null;
        public String       startTime     = null;
        public String       endTime       = null;
        public String       scheduleId    = null;
        public String       schPri        = null;
        public String       playbillId    = null;
        public String       programId     = null;
        public String       programName   = null;
        public String       pgmPri        = null;
        public String       verifyCode    = null;
        public long         termTimePoint = 0;
        public long         breakPoint    = 0;
        public boolean      playFinished  = false;
        public boolean      ignoreDLLimit = false;
        public ProgramLists programList   = null;
    }
    
    // 当前播放的节目
    private int                    mStatus                = IDLE_STATE;
    private ProgramInfo            mNormalProgram         = null;
    private ProgramInfo            mUrgentProgram         = null;
    private ArrayList<ProgramInfo> mNormalProgramInfoList = null;
    private ArrayList<ProgramInfo> mUrgentProgramInfoList = null;
    private String                 mCurrentNormalPgmVerifyCode = null;
    private String                 mCurrentUrgentPgmVerifyCode = null;
    
    private ScreenManager(Context context)
    {
        /*
         * This Class is a single instance mode, and define a private constructor to avoid external use the 'new'
         * keyword to instantiate a objects directly.
         */
        mContext = context;
    }
    
    public static ScreenManager createInstance(Context context)
    {
        if (mScreenManagerInstance == null && context != null)
        {
            mScreenManagerInstance = new ScreenManager(context);
        }
        return mScreenManagerInstance;
    }
    
    public static ScreenManager getInstance()
    {
        return mScreenManagerInstance;
    }
    
    public void setToNoStorage()
    {
        mStatus = NO_STORAGE_STATE;
    }
    
    public int getStatus()
    {
        return mStatus;
    }
    
    public HashMap<String, String> getCurrentNormalFilelist()
    {
        return getCurrentFilelist(mNormalProgramInfoList);
    }
    
    public HashMap<String, String> getCurrentEmergencyFilelist()
    {
        return getCurrentFilelist(mUrgentProgramInfoList);
    }
    
    private HashMap<String, String> getCurrentFilelist(ArrayList<ProgramInfo> programInfoList)
    {
        HashMap<String, String> nFileList = new HashMap<String, String>();
        if (programInfoList != null)
        {
            StringBuilder sbt = new StringBuilder();
            Time pgmEndTime = new Time();
            long currentTime = System.currentTimeMillis();
            
            // 初始化变量
            Areas area = null;
            PlayLists playlist = null;
            Medias media = null;
            PlayFiles playFile = null;
            StringBuilder sb = null;
            List<Areas> areaList = null;
            
            // 遍历节目单，找出在当前时间之前开始播放但没有结束的节目单
            for (ProgramInfo tempPgmInfo : programInfoList)
            {
                // 当前日期为有效期，则该节目为有效节目
                sbt.setLength(0);
                sbt.append(tempPgmInfo.endDate.replace("-", ""));
                sbt.append("T");
                sbt.append(tempPgmInfo.endTime.replace(":", ""));
                pgmEndTime.parse(sbt.toString());
                if (currentTime < pgmEndTime.toMillis(false))
                {
                    if (tempPgmInfo != null && tempPgmInfo.programList != null && tempPgmInfo.programList.Area != null)
                    {
                        // 初始化变量
                        area = null;
                        playlist = null;
                        media = null;
                        playFile = null;
                        sb = new StringBuilder();
                        areaList = tempPgmInfo.programList.Area;
                        
                        // 遍历Area，解析节目的窗体信息
                        for (int i = 0; i < areaList.size(); i++)
                        {
                            area = areaList.get(i);
                            
                            // Play List
                            if (area.Playlist != null)
                            {
                                for (int j = 0; j < area.Playlist.size(); j++)
                                {
                                    playlist = area.Playlist.get(j);
                                    
                                    // Media List
                                    if (playlist.Media != null)
                                    {
                                        for (int k = 0; k < playlist.Media.size(); k++)
                                        {
                                            media = playlist.Media.get(k);
                                            
                                            // Play File List
                                            if (media.PlayFile != null)
                                            {
                                                for (int l = 0; l < media.PlayFile.size(); l++)
                                                {
                                                    /***** 窗口的所有素材 ******/
                                                    playFile = media.PlayFile.get(l);
                                                    if (playFile.PlayFile != null && playFile.PlayFile.get("type") != null && "File".equals(playFile.PlayFile.get("type")))
                                                    {
                                                        sb.setLength(0);
                                                        sb.append(PosterApplication.getProgramPath());
                                                        sb.append(File.separator);
                                                        sb.append(media.Media.get("ptype"));
                                                        sb.append(File.separator);
                                                        sb.append(FileUtils.getFilename(playFile.FileName));
                                                        nFileList.put(FileUtils.getFilename(playFile.FileName), sb.toString());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return nFileList;
    }
    
    // 获取当前播放的Program ID
    public String getPlayingPgmId()
    {
        String retId = "";
        if (mStatus == PLAYING_EMERGENCE_PROGRAM && mUrgentProgram != null)
        {
            retId = mUrgentProgram.programId;
        }
        else if (mStatus == PLAYING_NORMAL_PROGRAM && mNormalProgram != null)
        {
            retId = mNormalProgram.programId;
        }
        return retId;
    }
    
    // 获取当前普通节目播放的schedule ID
    public String getNormalPlaySchId()
    {
        String retId = "";
        if (mStatus == PLAYING_NORMAL_PROGRAM && mNormalProgram != null)
        {
            retId = mNormalProgram.scheduleId;
        }
        return retId;
    }
    
    // 获取当前插播节目播放的schedule ID
    public String getUrgentPlaySchId()
    {
        String retId = "";
        if (mStatus == PLAYING_EMERGENCE_PROGRAM && mUrgentProgram != null)
        {
            retId = mUrgentProgram.scheduleId;
        }
        return retId;
    }
    
    /**********************************************
     * Start Screen Daemon Thread *
     **********************************************/
    public void startRun()
    {
        stopRun();
        mStatus = IDLE_STATE;
        mScreenDaemonThread = new ScreenDaemon();
        mScreenDaemonThread.setRunFlag(true);
        mScreenDaemonThread.start();
    }
    
    /**********************************************
     * Stop Screen Daemon Thread *
     **********************************************/
    public void stopRun()
    {
        if (mScreenDaemonThread != null)
        {
            mScreenDaemonThread.setRunFlag(false);
            mScreenDaemonThread.interrupt();
            mScreenDaemonThread = null;
        }
        
        FtpHelper.getInstance().cancelAllUploadThread();
        FtpHelper.getInstance().cancelAllDownloadThread();
        
        mHandler.removeMessages(EVENT_SHOW_IDLE_PROGRAM);
        mHandler.removeMessages(EVENT_SHOW_NORMAL_PROGRAM);
        mHandler.removeMessages(EVENT_SHOW_URGENT_PROGRAM);
    }
    
    public boolean isRuning()
    {
        return (mScreenDaemonThread != null && mScreenDaemonThread.getRunFlag());
    }
    
    public void setPrgFinishedFlag(boolean isPlayFinished)
    {
        if (mScreenDaemonThread != null)
        {
            mScreenDaemonThread.setPlayFinishedFlag(isPlayFinished);
        }
    }
    
    public void osdNotify(int opt)
    {
        if (opt == CLEAR_PGM_OPERATE)
        {
            if (mScreenDaemonThread != null)
            {
                mScreenDaemonThread.osdClearProgram();
            }
        }
        else if (opt == UPDATE_PGM_OPERATE)
        {
            if (mScreenDaemonThread != null)
            {
                mScreenDaemonThread.osdUpdateProgram();
            }
        }
    }
    
    // 屏幕管理守护线程
    @SuppressLint("DefaultLocale")
    private final class ScreenDaemon extends Thread
    {
        private boolean                mIsRun                   = false;
        
        private String                 mNormalPgmFilePath       = null;
        private String                 mUrgentPgmFilePath       = null;
        
        private int                    mSamePriNormalPgmListIdx = 0;
        private int                    mSamePriUrgentPgmListIdx = 0;
        private ArrayList<ProgramInfo> mSamePriNormalPgmList    = null;
        private ArrayList<ProgramInfo> mSamePriUrgentPgmList    = null;
        
        public void setRunFlag(boolean bIsRun)
        {
            mIsRun = bIsRun;
        }
        
        public boolean getRunFlag()
        {
            return mIsRun;
        }
        
        public void setPlayFinishedFlag(boolean isPlayFinished)
        {
            if (mStatus == PLAYING_EMERGENCE_PROGRAM)
            {
                if (mUrgentProgram != null)
                {
                    mUrgentProgram.playFinished = isPlayFinished;
                }
            }
            else if (mStatus == PLAYING_NORMAL_PROGRAM)
            {
                if (mNormalProgram != null)
                {
                    mNormalProgram.playFinished = isPlayFinished;
                }
            }
        }
        
        public void osdClearProgram()
        {
            mUrgentProgram = null;
            mNormalProgram = null;
            mUrgentProgramInfoList = null;
            mNormalProgramInfoList = null;
            mCurrentUrgentPgmVerifyCode = null;
            mCurrentNormalPgmVerifyCode = null;
            mStatus = IDLE_STATE;
        }
        
        public void osdUpdateProgram()
        {
            mUrgentPgmFilePath = obtainUrgentPgmFilePath();
            mUrgentProgramInfoList = getProgramScheduleFromXml(mUrgentPgmFilePath);
            mNormalPgmFilePath = obtainNormalPgmFilePath();
            mNormalProgramInfoList = getProgramScheduleFromXml(mNormalPgmFilePath);
            mStatus = IDLE_STATE;
        }
        
        private boolean normalPgmIsValid()
        {
            /* 若节目超时, 则该节目失效 */
            return ((mNormalProgram != null)
            		&& (mNormalProgram.termTimePoint >= System.currentTimeMillis()));
        }
        
        private boolean urgentPgmIsValid()
        {
            /* 若节目超时, 则该节目失效 */
            return (mUrgentProgram != null && mUrgentProgram.termTimePoint >= System.currentTimeMillis());
        }
        
        private boolean normalPgmIsBreak()
        {
            /* 若节目的打断时间点到时，则该节目将被打断  */
            return (mNormalProgram != null && mNormalProgram.breakPoint <= System.currentTimeMillis());
        }
        
        private boolean urgentPgmIsBreak()
        {
            /* 若节目的打断时间点到时，则该节目将被打断  */
            return (mUrgentProgram != null && mUrgentProgram.breakPoint <= System.currentTimeMillis());
        }
        
        private String obtainNormalPgmFilePath()
        {
            StringBuilder sb = new StringBuilder();
            sb.append(PosterApplication.getProgramPath());
            sb.append(File.separator);
            sb.append("playlist");
            sb.append(File.separator);
            sb.append(PosterApplication.LOCAL_NORMAL_PLAYLIST_FILENAME);
            return sb.toString();
        }
        
        private String obtainUrgentPgmFilePath()
        {
            StringBuilder sb = new StringBuilder();
            sb.append(PosterApplication.getProgramPath());
            sb.append(File.separator);
            sb.append("playlist");
            sb.append(File.separator);
            sb.append(PosterApplication.LOCAL_EMGCY_PLAYLIST_FILENAME);
            return sb.toString();
        }
        
        @Override
        public void run()
        {
            Logger.i("New ScreenDaemon thread, id is: " + currentThread().getId());
            
            mNormalPgmFilePath = obtainNormalPgmFilePath();
            mUrgentPgmFilePath = obtainUrgentPgmFilePath();
            mUrgentProgramInfoList = getProgramScheduleFromXml(mUrgentPgmFilePath);
            mNormalProgramInfoList = getProgramScheduleFromXml(mNormalPgmFilePath);
            
            while (mIsRun)
            {
                try
                {
                	/******************************************************
                	 * 因为Boot Action在双屏异显调用之前到达，所以等待双屏异显功能就绪   *
                	 ******************************************************/
                	if (PosterApplication.getInstance().isRecvBootAction())
                	{
                		Logger.i("Wait for extend screen OK..........");
                		PosterApplication.getInstance().setRecvBootFlag(false);
                		Thread.sleep(15000);
                	}
                	
					if (AuthorizationManager.getInstance().getStatus() == AuthorizationManager.STATUS_AUTHORIZED) {
						if (AuthorizationActivity.INSTANCE != null) {
							AuthorizationActivity.INSTANCE.finish();
							while (AuthorizationActivity.INSTANCE != null) {
								Thread.sleep(100);
							}
						}
					} else {
						if (AuthorizationActivity.INSTANCE == null) {
							Logger.i("start AuthorizationActivity ....");
							if (mContext != null) {
								((Activity) mContext).startActivity(new Intent(
										mContext, AuthorizationActivity.class));
								while (AuthorizationActivity.INSTANCE == null) {
									Thread.sleep(100);
								}
							}
						}
						Thread.sleep(1000);
						continue;
					}
                	
                    if (mOsdIsOpen)
                    {
                        Thread.sleep(1000);
                        continue;
                    }
                    
                    if (mUrgentPgmFileHasChanged)
                    {
                        /* 服务器更新插播节目 */
                        mUrgentPgmFileHasChanged = false;
                        mUrgentPgmFilePath = obtainUrgentPgmFilePath();
                        mUrgentProgramInfoList = getProgramScheduleFromXml(mUrgentPgmFilePath);
                        mUrgentProgram = getPlayProgram(mUrgentProgramInfoList, ERGENT_PROGRAM);
                        
                        if (mUrgentProgram != null)
                        {
                            Logger.i("Update urgent program name is: " + mUrgentProgram.programName);
                            if (loadProgramContent(EVENT_SHOW_URGENT_PROGRAM, mUrgentProgram))
                            {
                                mStatus = PLAYING_EMERGENCE_PROGRAM;
                            }
                            else
                            {
                                mStatus = IDLE_STATE;
                                Logger.i("Update urgent program failure, go to Idle.");
                            }
                        }
                    }
                    else if (mNormalPgmFileHasChanged)
                    {
                        /* 服务器更新普通节目 */
                        mNormalPgmFileHasChanged = false;
                        mNormalPgmFilePath = obtainNormalPgmFilePath();
                        mNormalProgramInfoList = getProgramScheduleFromXml(mNormalPgmFilePath);
                        mNormalProgram = getPlayProgram(mNormalProgramInfoList, NORMAL_PROGRAM);
                        
                        if (mNormalProgram != null && mStatus != PLAYING_EMERGENCE_PROGRAM)
                        {
                            Logger.i("Update normal program name is: " + mNormalProgram.programName);
                            if (loadProgramContent(EVENT_SHOW_NORMAL_PROGRAM, mNormalProgram))
                            {
                                mStatus = PLAYING_NORMAL_PROGRAM;
                            }
                            else
                            {
                                mStatus = IDLE_STATE;
                                Logger.i("Update normal program failure, go to Idle.");
                            }
                        }
                    }
                    else if (mStatus == PLAYING_EMERGENCE_PROGRAM && urgentPgmIsBreak())
                    {
                        mUrgentProgram.breakPoint = mUrgentProgram.termTimePoint;
                        Logger.i("Current urgent program is break.");
                        
                        /* check whether has emergence program to play */
                        if ((mUrgentProgram = obtainUrgentProgram()) != null)
                        {
                            Logger.i("change program name is: " + mUrgentProgram.programName);
                            if (loadProgramContent(EVENT_SHOW_URGENT_PROGRAM, mUrgentProgram))
                            {
                                // 节目加载成功
                                mStatus = PLAYING_EMERGENCE_PROGRAM;
                            }
                        }
                    }
                    else if ((mStatus == PLAYING_NORMAL_PROGRAM) && normalPgmIsBreak())
                    {
                        mNormalProgram.breakPoint = mNormalProgram.termTimePoint;
                        Logger.i("Current normal program is breaked.");
                        
                        /* check whether has emergence program to play */
                        if ((mUrgentProgram = obtainUrgentProgram()) != null)
                        {
                            Logger.i("change urgent program name is: " + mUrgentProgram.programName);
                            if (loadProgramContent(EVENT_SHOW_URGENT_PROGRAM, mUrgentProgram))
                            {
                                // 节目加载成功
                                mStatus = PLAYING_EMERGENCE_PROGRAM;
                            }
                        }
                        else if ((mNormalProgram = obtainNormalProgram()) != null)
                        {
                            /* check whether has Normal program to play */
                            Logger.i("change normal program name is: " + mNormalProgram.programName);
                            if (loadProgramContent(EVENT_SHOW_NORMAL_PROGRAM, mNormalProgram))
                            {
                                // 节目加载成功
                                mStatus = PLAYING_NORMAL_PROGRAM;
                            }
                        }
                    }
                    
                    /* 播放状态控制 */
                    switch (mStatus)
                    {
                    case IDLE_STATE:
                        
                        mNormalProgram = null;
                        mUrgentProgram = null;
                        mCurrentUrgentPgmVerifyCode = null;
                        mCurrentNormalPgmVerifyCode = null;
                        if ((mUrgentProgram = obtainUrgentProgram()) != null)
                        {
                            /* check whether has emergence program to play */
                            Logger.i("Start urgent program name is: " + mUrgentProgram.programName);
                            if (loadProgramContent(EVENT_SHOW_URGENT_PROGRAM, mUrgentProgram))
                            {
                                // 节目加载成功
                                mStatus = PLAYING_EMERGENCE_PROGRAM;
                            }
                            continue;
                        }
                        else if ((mNormalProgram = obtainNormalProgram()) != null)
                        {
                            /* check whether has Normal program to play */
                            Logger.i("Start normal program name is: " + mNormalProgram.programName);
                            if (loadProgramContent(EVENT_SHOW_NORMAL_PROGRAM, mNormalProgram))
                            {
                                // 节目加载成功
                                mStatus = PLAYING_NORMAL_PROGRAM;
                            }
                            continue;
                        }
                        else if (!mStandbyScreenIsShow)
                        {
                            /* Show standby screen */
                            Logger.i("Start standby screen.");
                            loadProgramContent(EVENT_SHOW_IDLE_PROGRAM, null);
                        }
                        
                        break;
                        
                    case NO_STORAGE_STATE:
                        
                        mNormalProgram = null;
                        mUrgentProgram = null;
                        mCurrentUrgentPgmVerifyCode = null;
                        mCurrentNormalPgmVerifyCode = null;
                        
                        if (!mStandbyScreenIsShow)
                        {
                            /* Show standby screen */
                            Logger.i("Start standby screen.");
                            loadProgramContent(EVENT_SHOW_IDLE_PROGRAM, null);
                        }
                        
                        if (PosterApplication.strogeIsAvailable())
                        {
                            mNormalPgmFilePath = obtainNormalPgmFilePath();
                            mUrgentPgmFilePath = obtainUrgentPgmFilePath();
                            mUrgentProgramInfoList = getProgramScheduleFromXml(mUrgentPgmFilePath);
                            mNormalProgramInfoList = getProgramScheduleFromXml(mNormalPgmFilePath);
                            mStatus = IDLE_STATE;
                        }
                        Thread.sleep(100);
                        continue;
                        
                    case PLAYING_NORMAL_PROGRAM:
                        
                        if (normalPgmIsValid())
                        {
                            /* 有相同优先级的节目，则循环播放节目 */
                            if (mNormalProgram.playFinished && mSamePriNormalPgmList != null && !normalPgmIsBreak())
                            {
                                if (++mSamePriNormalPgmListIdx >= mSamePriNormalPgmList.size())
                                {
                                    mSamePriNormalPgmListIdx = 0;
                                }
                                mNormalProgram = mSamePriNormalPgmList.get(mSamePriNormalPgmListIdx);
                                loadProgramContent(EVENT_SHOW_NORMAL_PROGRAM, mNormalProgram);
                            }
                        }
                        else
                        {
                            mNormalProgram = null;
                            mCurrentNormalPgmVerifyCode = null;
                            mStatus = IDLE_STATE;
                            Logger.i("Normal program is invalid, go to IDLE status.");
                            continue;
                        }
                        
                        break;
                    
                    case PLAYING_EMERGENCE_PROGRAM:
                        
                        if (urgentPgmIsValid())
                        {
                            if (mUrgentProgram.playFinished && mSamePriUrgentPgmList != null && !urgentPgmIsBreak())
                            {
                                /* 有相同优先级的节目，则循环播放节目 */
                                if (++mSamePriUrgentPgmListIdx >= mSamePriUrgentPgmList.size())
                                {
                                    mSamePriUrgentPgmListIdx = 0;
                                }
                                mUrgentProgram = mSamePriUrgentPgmList.get(mSamePriUrgentPgmListIdx);
                                loadProgramContent(EVENT_SHOW_URGENT_PROGRAM, mUrgentProgram);
                            }
                        }
                        else if ((mUrgentProgram = obtainUrgentProgram()) != null)
                        {
                            // 换插播节目
                            Logger.i("Urgent program has been changed....");
                            loadProgramContent(EVENT_SHOW_URGENT_PROGRAM, mUrgentProgram);
                        }
                        else if (normalPgmIsValid())
                        {
                        	Logger.i("Urgent program is invalid, and normal program is valid, go to PLAYING_NORMAL_PROGRAM status.");
                        	
                        	// Clean urgent program's flag
                            mUrgentProgram = null;
                            mCurrentUrgentPgmVerifyCode = null;
                            
                            // Re-start play normal program
                            if (loadProgramContent(EVENT_SHOW_NORMAL_PROGRAM, mNormalProgram))
                            {
                                // 节目加载成功
                                mStatus = PLAYING_NORMAL_PROGRAM;
                            }
                            
                            continue;
                        }
                        else
                        {
                            mNormalProgram = null;
                            mUrgentProgram = null;
                            mCurrentUrgentPgmVerifyCode = null;
                            mCurrentNormalPgmVerifyCode = null;
                            Logger.i("Urgent and normal program both invalid, go to IDLE status.");
                            mStatus = IDLE_STATE;
                            continue;
                        }
                        
                        break;
                    }
                    
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    Logger.i("ScreenDaemon Thread sleep over, and safe exit, the Thread id is: " + currentThread().getId());
                    return;
                }
                catch (Exception e)
                {
                    Logger.e("ScreenDaemon Thread Catch a error");
                    e.printStackTrace();
                    try
                    {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException e1)
                    {
                        Logger.i("ScreenDaemon Thread sleep over, and safe exit, the Thread id is: " + currentThread().getId());
                        return;
                    }
                }
            }
            
            Logger.i("ScreenDaemon Thread is safe Terminate, id is: " + currentThread().getId());
        }
        
        /*
         * 重新获取下一组需要播放的紧急节目
         */
        private ProgramInfo obtainUrgentProgram()
        {
            // 重新获取节目
            return (getPlayProgram(mUrgentProgramInfoList, ERGENT_PROGRAM));
        }
        
        /*
         * 重新获取下一组需要播放的普通节目
         */
        private ProgramInfo obtainNormalProgram()
        {
            // 重新获取节目
            return (getPlayProgram(mNormalProgramInfoList, NORMAL_PROGRAM));
        }

        /*
         * 加载节目内容
         */
        private boolean loadProgramContent(int msgId, ProgramInfo pgmInfo) throws InterruptedException
        {
            if (mOsdIsOpen)
            {
                Logger.e("loadProgramContent(): Osd already open, can't load program.");
                return false;
            }
            
            else if (msgId == EVENT_SHOW_URGENT_PROGRAM &&
                     pgmInfo.verifyCode.equals(mCurrentUrgentPgmVerifyCode))
            {
                /*
                 * 服务器通知节目改变的原因之一
                 * 有可能是因为本地的节目列表丢失了，所以需要检查外部存储是否被拔走
                 */
                if (!PosterApplication.strogeIsAvailable())
                {
                    mStatus = NO_STORAGE_STATE;
                }
                
                Logger.i("loadProgramContent(): urgent program is playing.");
                return true;
            }
            else if (msgId == EVENT_SHOW_NORMAL_PROGRAM &&
                     pgmInfo.verifyCode.equals(mCurrentNormalPgmVerifyCode))
            {
                /*
                 * 服务器通知节目改变的原因之一
                 * 有可能是因为本地的节目列表丢失了，所以需要检查外部存储是否被拔走
                 */
                if (!PosterApplication.strogeIsAvailable())
                {
                    mStatus = NO_STORAGE_STATE;
                }
                
                Logger.i("loadProgramContent(): normal program is playing.");
                return true;
            }  

            // Stop the FTP download for the last program
            stopFtpDownloadMaterials();
            
            // 获取窗口信息
            ArrayList<SubWindowInfoRef> subWndList = null;
            if (msgId == EVENT_SHOW_IDLE_PROGRAM)
            {
                subWndList = getStandbyWndInfoList();
            }
            else
            {
                subWndList = getSubWindowCollection(pgmInfo);
            }
            
            if (subWndList == null)
            {
                Logger.e("loadProgramContent(): No subwindow info.");
                return false;
            }

            // 清空标志
            mLoadProgramDone = false;
            if (pgmInfo != null)
            {
                pgmInfo.playFinished = false;
            }
            
            // 准备参数
            Bundle bundle = new Bundle();
            bundle.putSerializable("subwindowlist", (Serializable) subWndList);
            
            // 发送消息
            Message msg = mHandler.obtainMessage();
            msg.what = msgId;
            msg.setData(bundle);
            mHandler.sendMessage(msg);
            
            // 等待完成
            while (!mLoadProgramDone)
            {
                Thread.sleep(100);
            }

            // 更新当前节目的VerifyCode
            if (msgId == EVENT_SHOW_URGENT_PROGRAM)
            {
            	mStandbyScreenIsShow = false;
                mCurrentUrgentPgmVerifyCode = pgmInfo.verifyCode;
            }
            else if (msgId == EVENT_SHOW_NORMAL_PROGRAM)
            {
            	mStandbyScreenIsShow = false;
                mCurrentNormalPgmVerifyCode = pgmInfo.verifyCode;
            }
            else if (msgId == EVENT_SHOW_IDLE_PROGRAM)
            {
            	mStandbyScreenIsShow = true;
            }
            
            return true;
        }
        
        /*
         * 从节目信息中获取所有窗体信息
         */
        private ArrayList<SubWindowInfoRef> getSubWindowCollection(ProgramInfo pgmInfo)
        {
            ArrayList<SubWindowInfoRef> subWndCollection = null;
            
            if (pgmInfo != null && pgmInfo.programList != null && pgmInfo.programList.Area != null)
            {
                // 初始化变量
                Areas area = null;
                PlayLists playlist = null;
                Medias media = null;
                PlayFiles playFile = null;
                MediaInfoRef playMediaInfo = null;
                List<MediaInfoRef> mediaList = null;
                String strFilePathName = null;
                String strFileSavePath = null;
                SubWindowInfoRef tmpSubWndInfo = null;
                StringBuilder sb = new StringBuilder();
                List<Areas> areaList = pgmInfo.programList.Area;
                FtpFileInfo toDownloadFile = null;
                LinkedList<FtpFileInfo> toDownloadList = null;
                LinkedList<FtpFileInfo> downloadTextSubList = null;
                LinkedList<FtpFileInfo> downloadImageSubList = null;
                LinkedList<FtpFileInfo> downloadOtherSubList = null;
                int nPgmScreenWidth = 0;
                int nPgmScreenHeight = 0;
                int nMd5Key = 0;

                // 获取MD5 Key
                if (pgmInfo.programList.Program != null)
                {
                    nMd5Key = PosterApplication.stringHexToInt(pgmInfo.programList.Program.get("verifyKey"));
                }
                
                // 获取节目所对应的屏尺寸信息
                if (pgmInfo.programList.Screen != null)
                {
                    if (pgmInfo.programList.Screen.get("width") != null)
                    {
                        nPgmScreenWidth = Integer.parseInt(pgmInfo.programList.Screen.get("width"));
                    }
                    
                    if (pgmInfo.programList.Screen.get("height") != null)
                    {
                        nPgmScreenHeight = Integer.parseInt(pgmInfo.programList.Screen.get("height"));
                    }
                }
                
                // 遍历Area，解析节目的窗体信息
                for (int i = 0; i < areaList.size(); i++)
                {
                    area = areaList.get(i);
                    mediaList = null;
                    
                    // 创建新的子窗体信息
                    tmpSubWndInfo = new SubWindowInfoRef();
                    
                    // ID、Type
                    if (area.Area != null)
                    {
                        tmpSubWndInfo.setSubWindowName(area.Area.get("id"));
                        tmpSubWndInfo.setSubWindowType(area.Area.get("type"));
                    }
                    
                    // Location
                    if (area.Location != null)
                    {
                        // 按比例自适应屏幕大小
                        tmpSubWndInfo.setWidth(calculateWidthScale(Integer.parseInt(area.Location.get("w")), nPgmScreenWidth));
                        tmpSubWndInfo.setHeight(calculateHeightScale(Integer.parseInt(area.Location.get("h")), nPgmScreenHeight));
                        tmpSubWndInfo.setXPos(calculateWidthScale(Integer.parseInt(area.Location.get("x")), nPgmScreenWidth));
                        tmpSubWndInfo.setYPos(calculateHeightScale(Integer.parseInt(area.Location.get("y")), nPgmScreenHeight));
                    }
                    
                    // Play List
                    if (area.Playlist != null)
                    {
                        for (int j = 0; j < area.Playlist.size(); j++)
                        {
                            playlist = area.Playlist.get(j);
                            
                            // Media List
                            if (playlist.Media != null)
                            {
                                for (int k = 0; k < playlist.Media.size(); k++)
                                {
                                    media = playlist.Media.get(k);
                                    if (media.Media != null && media.Media.get("type").endsWith("BroadcastVideo"))
                                    {
                                        Logger.i("can't support broadcast Video type.");
                                        continue;
                                    }
                                    
                                    // Play File List
                                    if (media.PlayFile != null)
                                    {
                                        for (int l = 0; l < media.PlayFile.size(); l++)
                                        {
                                            /***** 窗口的所有素材 ******/
                                            playFile = media.PlayFile.get(l);
                                            
                                            if (playFile.PlayFile != null && 
                                                playFile.PlayFile.get("type") != null && 
                                                "File".equals(playFile.PlayFile.get("type")))
                                            {
                                                sb.setLength(0);
                                                sb.append(PosterApplication.getProgramPath());
                                                sb.append(File.separator);
                                                sb.append(media.Media.get("ptype"));
                                                sb.append(File.separator);
                                                sb.append(FileUtils.getFilename(playFile.FileName));
                                                strFilePathName = sb.toString();
                                                
                                                // 文件不在下载列表，且文件不存在或者MD5不正确,则添加到FTP下载队列中
                                                if ((!FileUtils.isExist(strFilePathName) || 
                                                    (FileUtils.isExist(strFilePathName) && 
                                                     !playFile.VerifyCode.equals(new Md5(nMd5Key).ComputeFileMd5(strFilePathName)))))
                                                {                                                   
                                                    sb.setLength(0);
                                                    sb.append(PosterApplication.getProgramPath());
                                                    sb.append(File.separator);
                                                    sb.append(playFile.FileName);
                                                    strFileSavePath = sb.toString();
                                                    
                                                    if (FileUtils.isExist(strFileSavePath) && 
                                                        playFile.VerifyCode.equals(new Md5(nMd5Key).ComputeFileMd5(strFileSavePath)))
                                                    {
                                                        // 如果是导出节目，用U盘更新，导出的文件存储位置与目标位置有可能不一样，此时以目标位置为准
                                                        strFilePathName = strFileSavePath;
                                                    }
                                                    else
                                                    {
                                                        if (FileUtils.isExist(strFilePathName))
                                                        {
                                                            // Existing file is wrong, will download later.
                                                            Logger.i("the verify code from xml is " + playFile.VerifyCode);
                                                            Logger.i("the computered verify code is " + new Md5(nMd5Key).ComputeFileMd5(strFilePathName));
                                                            FileUtils.delFile(new File(strFilePathName));
                                                        }

                                                        // Build ftp file info
                                                        sb.setLength(0);
                                                        sb.append(PosterApplication.getProgramPath());
                                                        sb.append(File.separator);
                                                        sb.append(media.Media.get("ptype"));
                                                        strFileSavePath = sb.toString();
                                                        
                                                        toDownloadFile = new FtpFileInfo();
                                                        toDownloadFile.setLocalPath(strFileSavePath);
                                                        toDownloadFile.setRemotePath(playFile.FileName);
                                                        toDownloadFile.setVerifyKey(nMd5Key);
                                                        toDownloadFile.setVerifyCode(playFile.VerifyCode);
                                                        
                                                        // add to the corresponding download list
                                                        if ("Background".equals(tmpSubWndInfo.getSubWindowType()))
                                                        {
                                                            // 背景图片最先下载
                                                            if (toDownloadList == null)
                                                            {
                                                                toDownloadList = new LinkedList<FtpFileInfo>();
                                                            }
                                                            
                                                            if (!toDownloadList.contains(toDownloadFile))
                                                            {
                                                                toDownloadList.addFirst(toDownloadFile);
                                                            }
                                                        }
                                                        else if (media.Media != null && "Text".equals(media.Media.get("type")))
                                                        {
                                                            if (downloadTextSubList == null)
                                                            {
                                                                downloadTextSubList = new LinkedList<FtpFileInfo>();
                                                            }
                                                            
                                                            if (!downloadTextSubList.contains(toDownloadFile))
                                                            {
                                                                downloadTextSubList.addLast(toDownloadFile);
                                                            }
                                                        }
                                                        else if (media.Media != null && "Image".equals(media.Media.get("type")))
                                                        {
                                                            if (downloadImageSubList == null)
                                                            {
                                                                downloadImageSubList = new LinkedList<FtpFileInfo>();
                                                            }
                                                            
                                                            if (!downloadImageSubList.contains(toDownloadFile))
                                                            {
                                                                downloadImageSubList.addLast(toDownloadFile);
                                                            }
                                                        }
                                                        else
                                                        {
                                                            if (downloadOtherSubList == null)
                                                            {
                                                                downloadOtherSubList = new LinkedList<FtpFileInfo>();
                                                            }
                                                            
                                                            if (!downloadOtherSubList.contains(toDownloadFile))
                                                            {
                                                                downloadOtherSubList.addLast(toDownloadFile);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            else
                                            {
                                                strFilePathName = playFile.Url;
                                            }
                                            
                                            // 创建新的playMediaInfo
                                            playMediaInfo = new MediaInfoRef();
                                            playMediaInfo.filePath = strFilePathName;
                                            playMediaInfo.md5Key = nMd5Key;
                                            playMediaInfo.verifyCode = playFile.VerifyCode;
                                            playMediaInfo.remotePath = playFile.FileName;
                                            if (media.Media != null)
                                            {
                                                playMediaInfo.mid = media.Media.get("id");
                                                playMediaInfo.vType = media.Media.get("type");
                                                playMediaInfo.mediaType = media.Media.get("ptype");
                                            }
                                            if (playFile.PlayFile != null)
                                            {
                                                playMediaInfo.source = playFile.PlayFile.get("type");
                                            }
                                            if (media.Duration != null)
                                            {
                                                playMediaInfo.duration = Integer.parseInt(media.Duration) * 1000;
                                            }
                                            if (media.Times != null)
                                            {
                                                playMediaInfo.times = Integer.parseInt(media.Times);
                                            }
                                            if (media.Mode != null)
                                            {
                                                playMediaInfo.mode = Integer.parseInt(media.Mode);
                                            }
                                            if (media.Aspect != null)
                                            {
                                                playMediaInfo.aspect = Integer.parseInt(media.Aspect);
                                            }
                                            if (media.Speed != null)
                                            {
                                                playMediaInfo.speed = Integer.parseInt(media.Speed);
                                            }
                                            if (media.Vol != null)
                                            {
                                                playMediaInfo.vol = Integer.parseInt(media.Vol);
                                            }
                                            if (media.Font != null)
                                            {
                                                playMediaInfo.fontName = media.Font.get("name");
                                                playMediaInfo.fontSize = media.Font.get("size");
                                                playMediaInfo.fontColor = media.Font.get("color");
                                            }
                                            if (media.DurationPerPage != null)
                                            {
                                                playMediaInfo.durationPerPage = Integer.parseInt(media.DurationPerPage) * 1000;
                                            }
                                            if (playlist.Playlist != null)
                                            {
                                                playMediaInfo.playlistmode = playlist.Playlist.get("mode");
                                                playMediaInfo.timetype = playlist.Playlist.get("timetype");
                                                playMediaInfo.starttime = playlist.Playlist.get("stime");
                                                playMediaInfo.endtime = playlist.Playlist.get("etime");
                                            }
                                            playMediaInfo.containerwidth = tmpSubWndInfo.getWidth();
                                            playMediaInfo.containerheight = tmpSubWndInfo.getHeight();
                                            playMediaInfo.isIgnoreDlLimit = pgmInfo.ignoreDLLimit;
                                            
                                            // 添加到Media list中
                                            if (mediaList == null)
                                            {
                                                mediaList = new ArrayList<MediaInfoRef>();
                                            }
                                            mediaList.add(playMediaInfo);
                                        }
                                    }
                                    else if ("Clock".equals(tmpSubWndInfo.getSubWindowType()))
                                    {
                                        /***** Clock 只有参数，没有素材 ******/
                                        playMediaInfo = new MediaInfoRef();
                                        playMediaInfo.format = media.Format;
                                        playMediaInfo.fontName = media.Font.get("name");
                                        playMediaInfo.fontSize = media.Font.get("size");
                                        playMediaInfo.fontColor = media.Font.get("color");
                                        playMediaInfo.containerwidth = tmpSubWndInfo.getWidth();
                                        playMediaInfo.containerheight = tmpSubWndInfo.getHeight();
                                        
                                        // 添加到Media list中
                                        if (mediaList == null)
                                        {
                                            mediaList = new ArrayList<MediaInfoRef>();
                                        }
                                        mediaList.add(playMediaInfo);
                                    }
                                    else if ("Timer".equals(tmpSubWndInfo.getSubWindowType()))
                                    {
                                    	playMediaInfo = new MediaInfoRef();
                                        playMediaInfo.format = media.Format;
                                        playMediaInfo.fontName = media.Font.get("name");
                                        playMediaInfo.fontSize = media.Font.get("size");
                                        playMediaInfo.fontColor = media.Font.get("color");
                                        if ("Countdown".equals(media.Mode))
                                        {
                                        	playMediaInfo.mode = 0;
                                        }
                                        else if ("Elapse".equals(media.Mode))
                                        {
                                        	playMediaInfo.mode = 1;
                                        }
                                        else
                                        {
                                        	playMediaInfo.mode = -1;
                                        }
                                        playMediaInfo.deadline = media.Deadline;
                                        playMediaInfo.containerwidth = tmpSubWndInfo.getWidth();
                                        playMediaInfo.containerheight = tmpSubWndInfo.getHeight();
                                        
                                        // 添加到Media list中
                                        if (mediaList == null)
                                        {
                                            mediaList = new ArrayList<MediaInfoRef>();
                                        }
                                        mediaList.add(playMediaInfo);
                                    }
                                }
                            }
                        }
                        
                        if (mediaList != null)
                        {
                            tmpSubWndInfo.setSubWndMediaList(mediaList);
                        }
                    }
                    
                    // 添加子窗体信息到窗口集合
                    if (subWndCollection == null)
                    {
                        subWndCollection = new ArrayList<SubWindowInfoRef>();
                    }
                    subWndCollection.add(tmpSubWndInfo);
                }
                
                // First download Text materials
                if (downloadTextSubList != null)
                {
                    if (toDownloadList == null)
                    {
                        toDownloadList = new LinkedList<FtpFileInfo>();
                    }
                    toDownloadList.addAll(downloadTextSubList);
                }
                
                // Second download Image materials
                if (downloadImageSubList != null)
                {
                    if (toDownloadList == null)
                    {
                        toDownloadList = new LinkedList<FtpFileInfo>();
                    }
                    toDownloadList.addAll(downloadImageSubList);
                }
                
                // Last download Video and others materials
                if (downloadOtherSubList != null)
                {
                    if (toDownloadList == null)
                    {
                        toDownloadList = new LinkedList<FtpFileInfo>();
                    }
                    toDownloadList.addAll(downloadOtherSubList);
                }
                
                // Trigger the FTP download
                if (toDownloadList != null && !toDownloadList.isEmpty())
                {
                    if (FtpHelper.getInstance().ftpDownloadIsWorking())
                    {
                        FtpHelper.getInstance().addMaterialsToDlQueue(toDownloadList, pgmInfo.ignoreDLLimit);
                    }
                    else
                    {
                        FtpHelper.getInstance().startDownloadPgmMaterials(toDownloadList, pgmInfo.ignoreDLLimit, null);
                    }
                }
            }
            
            return subWndCollection;
        }
        
        /*
         * 从节目清单中获取下一时间段要播放的节目
         */
        private ProgramInfo getNextProgram(ArrayList<ProgramInfo> pgmSchedule)
        {
            if (pgmSchedule == null)
            {
                return null;
            }
            
            // 初始化临时变量
            ProgramInfo nextPgmInfo = null;
            ArrayList<ProgramInfo> nextPgmSchedule = null;
            long startDate, endDate, todayStartTime = 0;
            StringBuilder sb = new StringBuilder();
            Time time = new Time();
            time.setToNow();
            sb.append(time.year);
            sb.append(((time.month + 1) < 10) ? ("0" + (time.month + 1)) : (time.month + 1));
            sb.append((time.monthDay < 10) ? ("0" + time.monthDay) : time.monthDay);
            String todayDate = sb.toString();
            sb.append("T");
            sb.append("000000");
            time.parse(sb.toString());
            long currentDate = time.toMillis(false);
            long currentTime = System.currentTimeMillis();
            
            // 遍历节目单，找出在当前时间之后开始播放的节目单
            for (ProgramInfo tempPgmInfo : pgmSchedule)
            {
                sb.setLength(0);
                sb.append(tempPgmInfo.startDate.replace("-", ""));
                sb.append("T");
                sb.append("000000");
                time.parse(sb.toString());
                startDate = time.toMillis(true);   // 开始日期
                
                sb.setLength(0);
                sb.append(tempPgmInfo.endDate.replace("-", ""));
                sb.append("T");
                sb.append("235959");
                time.parse(sb.toString());
                endDate = time.toMillis(false);    // 结束日期
                
                sb.setLength(0);
                sb.append(todayDate);
                sb.append("T");
                sb.append(tempPgmInfo.startTime.replace(":", ""));
                time.parse(sb.toString());
                todayStartTime = time.toMillis(false);    // 当天的开始时间                
                
                // 当前日期在节目有效期内
                if (currentDate >= startDate  && currentDate <= endDate)
                {
                    if (todayStartTime > currentTime)
                    {
                        // 节目当天的开始时间在当前时间之后，则该节目为当天下一时间段需要播放的节目
                        if (nextPgmSchedule == null)
                        {
                            nextPgmSchedule = new ArrayList<ProgramInfo>();
                        }
                        nextPgmSchedule.add(tempPgmInfo);
                    }
                }
            }
            
            // 遍历下一轮播放的节目单，找出待播放的节目
            if (nextPgmSchedule != null)
            {
                long nextPgmStartTime = 0;
                long tempPgmStartTime = 0;
                for (ProgramInfo tempPgmInfo : nextPgmSchedule)
                {
                    if (nextPgmInfo != null)
                    {
                        sb.setLength(0);
                        sb.append(todayDate);
                        sb.append("T");
                        sb.append(tempPgmInfo.startTime.replace(":", ""));
                        time.parse(sb.toString());
                        tempPgmStartTime = time.toMillis(false);
                        
                        sb.setLength(0);
                        sb.append(todayDate);
                        sb.append("T");
                        sb.append(nextPgmInfo.startTime.replace(":", ""));
                        time.parse(sb.toString());
                        nextPgmStartTime = time.toMillis(false);
                        
                        if (tempPgmStartTime < nextPgmStartTime  ||
                            (tempPgmStartTime == nextPgmStartTime &&
                            (Integer.parseInt(tempPgmInfo.schPri) > Integer.parseInt(nextPgmInfo.schPri) ||
                            (Integer.parseInt(tempPgmInfo.schPri) == Integer.parseInt(nextPgmInfo.schPri) &&
                             Integer.parseInt(tempPgmInfo.pgmPri) >  Integer.parseInt(nextPgmInfo.pgmPri)))))
                        {
                            nextPgmInfo = tempPgmInfo;
                        }
                    }
                    else
                    {
                        nextPgmInfo = tempPgmInfo;
                    }
                }
            }
            
            return nextPgmInfo;
        }
        
        /*
         * 从节目清单中获取将要播放的节目
         */
        private ProgramInfo getPlayProgram(ArrayList<ProgramInfo> pgmSchedule, int nProgramType)
        {
            if (pgmSchedule == null)
            {
                return null;
            }

            // 清空相同优先级的节目列表
            if (nProgramType == NORMAL_PROGRAM)
            {
                mSamePriNormalPgmList = null;
                mSamePriNormalPgmListIdx = 0;
            }
            else if (nProgramType == ERGENT_PROGRAM)
            {
                mSamePriUrgentPgmList = null;
                mSamePriUrgentPgmListIdx = 0;
            }
            
            // 初始化临时变量
            ProgramInfo currentPgmInfo = null;
            ArrayList<ProgramInfo> currentPgmSchedule = null;
            ArrayList<ProgramInfo> samePgmSchedule = new ArrayList<ProgramInfo>();
            long startDate, endDate, todayStartTime, todayEndTime = 0;
            StringBuilder sb = new StringBuilder();
            Time time = new Time();
            time.setToNow();
            sb.append(time.year);
            sb.append(((time.month + 1) < 10) ? ("0" + (time.month + 1)) : (time.month + 1));
            sb.append((time.monthDay < 10) ? ("0" + time.monthDay) : time.monthDay);
            String todayDate = sb.toString();
            sb.append("T");
            sb.append("000000");
            time.parse(sb.toString());
            long currentDate = time.toMillis(false);
            long currentTime = System.currentTimeMillis();

            // 遍历节目单，找出在当前时间之前开始播放但没有结束的节目单
            for (ProgramInfo tempPgmInfo : pgmSchedule)
            {
                sb.setLength(0);
                sb.append(tempPgmInfo.startDate.replace("-", ""));
                sb.append("T");
                sb.append("000000");
                time.parse(sb.toString());
                startDate = time.toMillis(true);   // 开始日期
                
                sb.setLength(0);
                sb.append(tempPgmInfo.endDate.replace("-", ""));
                sb.append("T");
                sb.append("235959");
                time.parse(sb.toString());
                endDate = time.toMillis(false);    // 结束日期
                
                sb.setLength(0);
                sb.append(todayDate);
                sb.append("T");
                sb.append(tempPgmInfo.startTime.replace(":", ""));
                time.parse(sb.toString());
                todayStartTime = time.toMillis(false);    // 当天的开始时间
                
                sb.setLength(0);
                sb.append(todayDate);
                sb.append("T");
                sb.append(tempPgmInfo.endTime.replace(":", ""));
                time.parse(sb.toString());
                todayEndTime = time.toMillis(false);       // 当天的结束时间
                
                // 当前日期在节目有效期内
                if (startDate <= currentDate && endDate >= currentDate)
                {
                    if (todayStartTime <= currentTime && todayEndTime > currentTime)
                    {
                        // 节目当天的开始时间在当前时间之前，结束时间在当前时间之后，则该节目为当前可播放节目
                        if (currentPgmSchedule == null)
                        {
                            currentPgmSchedule = new ArrayList<ProgramInfo>();
                        }
                        tempPgmInfo.breakPoint = todayEndTime;
                        tempPgmInfo.termTimePoint = todayEndTime;
                        currentPgmSchedule.add(tempPgmInfo);
                    }
                }
            }
            
            // 遍历当前播放的节目单，找出优先级最高的节目为当前播放节目
            if (currentPgmSchedule != null)
            {
                int nMaxSchPri = -1;
                for (ProgramInfo tempPgmInfo : currentPgmSchedule)
                {
                    if (Integer.parseInt(tempPgmInfo.schPri) > nMaxSchPri)
                    {
                        nMaxSchPri = Integer.parseInt(tempPgmInfo.schPri);
                        currentPgmInfo = tempPgmInfo;
                    }
                    else if (currentPgmInfo != null && 
                             Integer.parseInt(currentPgmInfo.schPri) == nMaxSchPri && 
                             Integer.parseInt(tempPgmInfo.pgmPri) > Integer.parseInt(currentPgmInfo.pgmPri))
                    {
                        currentPgmInfo = tempPgmInfo;
                    }
                }
            }
            
            // 计算播放节目的打断时间点(在下一个播放节目的开始时间在当前节目的结束时间之前，则更新节目的打断时间点)
            int toPlaySchPri = 0;
            int toPlayPgmPri = 0;
            ProgramInfo toPlayPgm = null;
            ProgramInfo nextPgmInfo = null;
            if (nProgramType == ERGENT_PROGRAM)
            {
                nextPgmInfo = getNextProgram(mUrgentProgramInfoList);
                if (currentPgmInfo != null)
                {
                    // Urgent节目被另一个Urgent节目打断
                    toPlayPgm = currentPgmInfo;
                    toPlayPgmPri = Integer.parseInt(toPlayPgm.pgmPri);
                    toPlaySchPri = Integer.parseInt(toPlayPgm.schPri);
                }
                else if (normalPgmIsValid())
                {
                    // Normal节目被另一个Urgent节目打断
                    toPlayPgm = mNormalProgram;
                }
            }
            else if (nProgramType == NORMAL_PROGRAM)
            {
                ProgramInfo nextUrgentPgmInfo = getNextProgram(mUrgentProgramInfoList);
                ProgramInfo nextNormalPgmInfo = getNextProgram(mNormalProgramInfoList);
                if (nextUrgentPgmInfo != null && nextNormalPgmInfo != null)
                {
                    if (PosterApplication.getTimeMillis(nextNormalPgmInfo.startTime) < PosterApplication.getTimeMillis(nextUrgentPgmInfo.startTime))
                    {
                        // Normal节目被另一个Normal节目打断
                        nextPgmInfo = nextNormalPgmInfo;
                        if (currentPgmInfo != null)
                        {
                            toPlayPgm = currentPgmInfo;
                            toPlayPgmPri = Integer.parseInt(toPlayPgm.pgmPri);
                            toPlaySchPri = Integer.parseInt(toPlayPgm.schPri);
                        }
                        else if (normalPgmIsValid())
                        {
                            toPlayPgm = mNormalProgram;
                            toPlayPgmPri = Integer.parseInt(toPlayPgm.pgmPri);
                            toPlaySchPri = Integer.parseInt(toPlayPgm.schPri);
                        }
                    }
                    else
                    {
                        // Normal节目被另一个Urgent节目打断
                        nextPgmInfo = nextUrgentPgmInfo;
                        if (currentPgmInfo != null)
                        {
                            toPlayPgm = currentPgmInfo;
                        }
                        else if (normalPgmIsValid())
                        {
                            toPlayPgm = mNormalProgram;
                        }
                    }
                }
                else if (nextUrgentPgmInfo != null)
                {
                    // Normal节目被另一个Urgent节目打断
                    nextPgmInfo = nextUrgentPgmInfo;
                    if (currentPgmInfo != null)
                    {
                        toPlayPgm = currentPgmInfo;
                    }
                    else if (normalPgmIsValid())
                    {
                        toPlayPgm = mNormalProgram;
                    }
                }
                else if (nextNormalPgmInfo != null)
                {
                    // Normal节目被另一个Normal节目打断
                    nextPgmInfo = nextNormalPgmInfo;
                    if (currentPgmInfo != null)
                    {
                        toPlayPgm = currentPgmInfo;
                        toPlayPgmPri = Integer.parseInt(toPlayPgm.pgmPri);
                        toPlaySchPri = Integer.parseInt(toPlayPgm.schPri);
                    }
                    else if (normalPgmIsValid())
                    {
                        toPlayPgm = mNormalProgram;
                        toPlayPgmPri = Integer.parseInt(toPlayPgm.pgmPri);
                        toPlaySchPri = Integer.parseInt(toPlayPgm.schPri);
                    }
                }  
            }

            if (nextPgmInfo != null && toPlayPgm != null)
            {
                sb.setLength(0);
                sb.append(todayDate);
                sb.append("T");
                sb.append(nextPgmInfo.startTime.replace(":", ""));
                time.parse(sb.toString());
                long nextPgmStartTime = time.toMillis(false);
                
                // 计算当前节目真正的终止时间点
                if (toPlayPgm.termTimePoint > nextPgmStartTime && 
                   (Integer.parseInt(nextPgmInfo.pgmPri) > toPlayPgmPri || Integer.parseInt(nextPgmInfo.schPri) > toPlaySchPri))
                {
                    toPlayPgm.breakPoint = nextPgmStartTime;
                }
            }
            
            // 找出和当前节目拥有相同播放时间、相同scedule、相同playbill，相同优先级的其他节目
            if (currentPgmSchedule != null && currentPgmSchedule.size() > 1)
            {
                for (ProgramInfo tempPgmInfo : currentPgmSchedule)
                {
                    if (tempPgmInfo.scheduleId.equals(currentPgmInfo.scheduleId) && 
                        tempPgmInfo.playbillId.equals(currentPgmInfo.playbillId) && 
                        tempPgmInfo.schPri.equals(currentPgmInfo.schPri) && 
                        tempPgmInfo.pgmPri.equals(currentPgmInfo.pgmPri) && 
                        tempPgmInfo.startDate.equals(currentPgmInfo.startDate) && 
                        tempPgmInfo.endDate.equals(currentPgmInfo.endDate) && 
                        tempPgmInfo.startTime.equals(currentPgmInfo.startTime) && 
                        tempPgmInfo.endTime.equals(currentPgmInfo.endTime))
                    {
                        tempPgmInfo.breakPoint = currentPgmInfo.breakPoint;
                        samePgmSchedule.add(tempPgmInfo);
                    }
                }
            }
            
            // 保存拥有相同播放时间、相同scedule、相同playbill，相同优先级的节目
            if (samePgmSchedule.size() > 1)
            {
                if (nProgramType == NORMAL_PROGRAM)
                {
                    mSamePriNormalPgmList = samePgmSchedule;
                }
                else if (nProgramType == ERGENT_PROGRAM)
                {
                    mSamePriUrgentPgmList = samePgmSchedule;
                }
            }
            
            return currentPgmInfo;
        }
        
        /*
         * 从XML文件中获取节目清单
         */
        private ArrayList<ProgramInfo> getProgramScheduleFromXml(String filePath)
        {
            ArrayList<ProgramInfo> programSchedule = null;
            
            // 解析Schedule XML文件
            ScheduleLists scheduList = null;
            if (FileUtils.isExist(filePath))
            {
                FileUtils.updateFileLastTime(filePath);  // 更新文件最后修改时间
                scheduList = (ScheduleLists) XmlFileParse(filePath, ScheduleLists.class);
            }
            else
            {
                return null;
            }
            
            // 创建新的节目单
            if (scheduList != null && scheduList.Schedule != null)
            {
                programSchedule = new ArrayList<ProgramInfo>();
                Schedules schedule = null;
                Playbills playbill = null;
                Programs program = null;
                ProgramLists tmpPgmList = null;
                ProgramInfo programInfo = null;
                int md5Key = 0x10325476;
                String md5Value = null;
                String strPgmName = null;
                StringBuilder sbPgmFileName = new StringBuilder();
                
                // 遍历所有的Schedule
                for (int i = 0; i < scheduList.Schedule.size(); i++)
                {
                    // 获取schedule中的信息
                    schedule = scheduList.Schedule.get(i);
                    if (schedule == null || schedule.Playbill == null)
                    {
                        Logger.w("No schedule info, skip it.");
                        continue;
                    }
                    
                    // 遍历Schedule中所有的playbill
                    for (int j = 0; j < schedule.Playbill.size(); j++)
                    {
                        // 获取playbill中的信息
                        playbill = schedule.Playbill.get(j);
                        
                        // 有效性验证
                        if (playbill == null || playbill.Program == null)
                        {
                            Logger.w("No playbill info, skip it.");
                            continue;
                        }
                        
                        // 遍历playbill中所有的program
                        for (int k = 0; k < playbill.Program.size(); k++)
                        {
                            // 获取program中的信息
                            program = playbill.Program.get(k);
                            
                            // 有效性验证
                            if (program == null || program.Program == null)
                            {
                                Logger.w("No program info, skip it.");
                                continue;
                            }
                            
                            // 解析 XML文件，获取节目内容
                            sbPgmFileName.setLength(0);
                            sbPgmFileName.append(PosterApplication.getProgramPath());
                            sbPgmFileName.append(File.separator);
                            sbPgmFileName.append("template");
                            sbPgmFileName.append(File.separator);
                            sbPgmFileName.append(program.Program.get("name"));
                            strPgmName = sbPgmFileName.toString();
                            if (FileUtils.isExist(strPgmName))
                            {
                                FileUtils.updateFileLastTime(strPgmName);  // 更新文件最后修改时间
                                tmpPgmList = (ProgramLists) XmlFileParse(strPgmName, ProgramLists.class);
                            }
                            else
                            {
                                Logger.e("[" + strPgmName + "] didn't exist.");
                                continue;
                            }
                            
                            // 校验program的Md5值
                            md5Key = 0x10325476;
                            if (tmpPgmList != null && tmpPgmList.Program != null)
                            {
                                md5Key = PosterApplication.stringHexToInt(tmpPgmList.Program.get("verifyKey"));
                            }
                            md5Value = new Md5(md5Key).ComputeMd5(strPgmName);
                            if (!md5Value.equals(program.Program.get("verify")))
                            {
                                Logger.e("xml file MD5 value is wrong, skip this program: " + strPgmName + " calculate md5Value is: " + md5Value);
                                continue;
                            }
                            
                            // 创建Program info
                            programInfo = new ProgramInfo();
                            programInfo.startDate = schedule.BeginDate;
                            programInfo.endDate = schedule.EndDate;
                            programInfo.schPri = schedule.PRI;
                            programInfo.scheduleId = schedule.Schedule.get("id");
                            programInfo.playbillId = playbill.Playbillid;
                            programInfo.startTime = playbill.BeginTime;
                            programInfo.endTime = playbill.EndTime;
                            programInfo.pgmPri = playbill.PRI;
                            programInfo.programId = program.Program.get("id");
                            programInfo.programName = program.Program.get("name");
                            programInfo.verifyCode = program.Program.get("verify");
                            programInfo.programList = tmpPgmList;
                            programInfo.ignoreDLLimit = "0".equals(schedule.DLASAP) ? false : true;
                            
                            ////////////////////////////////////////////////////////////////////////////
                            // This patch for the case of the playing program and update program are same,
                            // it should keep the play status same. 
                            if (mStatus == PLAYING_EMERGENCE_PROGRAM && 
                                mUrgentProgram != null &&
                                mUrgentProgram.verifyCode != null &&
                                mUrgentProgram.verifyCode.equals(programInfo.verifyCode))
                            {
                                programInfo.playFinished = mUrgentProgram.playFinished;
                            }
                            else if (mStatus == PLAYING_NORMAL_PROGRAM && 
                                     mNormalProgram != null &&
                                     mNormalProgram.verifyCode != null &&
                                     mNormalProgram.verifyCode.equals(programInfo.verifyCode))
                            {
                                programInfo.playFinished = mNormalProgram.playFinished;
                            }
                            else
                            {
                                programInfo.playFinished = false;
                            }
                            /////////////////////////////////////////////////////////////////////////////
                            
                            // 添加节目信息
                            programSchedule.add(programInfo);
                        }
                    }
                }
            }
            
            return programSchedule;
        }
        
        // 解析xml文件
        private Object XmlFileParse(String path, Class<?> clazz)
        {
            Object obj = null;
            FileInputStream fin;
            
            synchronized (mProgramFileLock)
            {
                try
                {
                    fin = new FileInputStream(path);
                }
                catch (FileNotFoundException e)
                {
                    fin = null;
                    e.printStackTrace();
                    return null;
                }
                
                XmlParser parser = new XmlParser();
                obj = parser.getXmlObject(fin, clazz);
            }
            
            return obj;
        }
        
        // 创建待机画面节目信息
        private ArrayList<SubWindowInfoRef> getStandbyWndInfoList()
        {
            ArrayList<SubWindowInfoRef> subWndInfoList = new ArrayList<SubWindowInfoRef>();
            List<MediaInfoRef> mediaList = new ArrayList<MediaInfoRef>();
            
            // 创建新的playMediaInfo
            MediaInfoRef playMediaInfo = new MediaInfoRef();
            playMediaInfo.filePath = PosterApplication.getInstance().getStandbyScreenImgPath();
            playMediaInfo.md5Key = 0;
            playMediaInfo.verifyCode = "";
            playMediaInfo.remotePath = "";
            playMediaInfo.mid = "0";
            playMediaInfo.vType = "Image";
            playMediaInfo.mediaType = "Image";
            playMediaInfo.source = "File";
            playMediaInfo.duration = 60 * 1000;
            playMediaInfo.times = 0;
            playMediaInfo.mode = 0;
            playMediaInfo.aspect = 0;
            playMediaInfo.speed = 0;
            playMediaInfo.vol = 0;
            playMediaInfo.durationPerPage = 60 * 1000;
            playMediaInfo.playlistmode = "loop";
            playMediaInfo.timetype = "absolute";
            playMediaInfo.starttime = null;
            playMediaInfo.endtime = null;
            playMediaInfo.containerwidth = PosterApplication.getScreenWidth();
            playMediaInfo.containerheight = PosterApplication.getScreenHeigth();
            playMediaInfo.isIgnoreDlLimit = false;
            mediaList.add(playMediaInfo);
            
            SubWindowInfoRef subWndInfo = new SubWindowInfoRef();
            subWndInfo.setSubWindowType("StandbyScreen");
            subWndInfo.setSubWindowName("StandbyScreenImage");
            subWndInfo.setWidth(PosterApplication.getScreenWidth());
            subWndInfo.setHeight(PosterApplication.getScreenHeigth());
            subWndInfo.setSubWndMediaList(mediaList);
            subWndInfoList.add(subWndInfo);
            
            return subWndInfoList;
        }
        
        // 按屏幕实际大小进行比例缩放(横向)
        private int calculateWidthScale(int nValue, int pgmWidth)
        {
            int ret = nValue;
            int nScreenWidth = PosterApplication.getScreenWidth();
            if (pgmWidth > 0 && nScreenWidth > 0)
            {
                ret = nValue * nScreenWidth / pgmWidth;
            }
            return PosterApplication.px2dip(mContext, ret);
        }
        
        // 按屏幕实际大小进行比例缩放(纵向)
        private int calculateHeightScale(int nValue, int pgmHeight)
        {
            int ret = nValue;
            int nScreenHeight = PosterApplication.getScreenHeigth();
            if (pgmHeight > 0 && nScreenHeight > 0)
            {
                ret = nValue * nScreenHeight / pgmHeight;
            }
            return PosterApplication.px2dip(mContext, ret);
        }

        // 终止素材下载
        private void stopFtpDownloadMaterials()
        {
            FtpHelper.getInstance().stopDownloadPgmMaterials();
        }
    }
    
    @SuppressLint("HandlerLeak")
    final Handler mHandler = new Handler() 
    {
        @SuppressWarnings("unchecked")
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
            case EVENT_SHOW_NORMAL_PROGRAM:
            case EVENT_SHOW_URGENT_PROGRAM:
            case EVENT_SHOW_IDLE_PROGRAM:
                if (mContext instanceof PosterMainActivity)
                {
                    ((PosterMainActivity) mContext).loadNewProgram((ArrayList<SubWindowInfoRef>) msg.getData().getSerializable("subwindowlist"));
                }
                mLoadProgramDone = true;
                return;
                
            default:
                break;
            }
            super.handleMessage(msg);
        }
    };
}
