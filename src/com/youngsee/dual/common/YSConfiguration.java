package com.youngsee.dual.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import android.app.Application;
import android.content.Context;

/**
 * The class of get all configuration from config.propertie. Created by
 * TianXuguang on 2015/8/8.
 */
public class YSConfiguration{
    private final static String    configFileName          = "config.properties";
    
    private final static String    key_feature             = "feature";
    private final static String    key_defualt_server_url  = "defualt_server_url";
    private final static String    key_boot_apk_package_name  = "boot_apk_package_name";
    private final static String    key_install_ysctrl      = "install_ysctrl";
    
    public final static String     FEATURE_CODE_YUESHI     = "YueShi";
    public final static String     FEATURE_CODE_COMMON     = "common";
    
    public final static String     BOOT_APK_PACKAGE_NAME_NONE = "None";
    
    private static YSConfiguration instance                = null;
    
    // For get the application context. It should has same life circle with
    // application.
    // Because this configuration is global in application.
    private static Application     mApplication            = null;
    
    private String                 mFeatureCode            = null;
    private String                 mServerUrl              = null;
    private String                 mBootApkPackageName     = null;
    private Boolean                mIsNeedInstallYsctrl    = null;
    
    /**
     * Get Configuration by this function to avoid create multiple object of
     * Configuration.
     * 
     * @return the object of Configuration
     */
    public static YSConfiguration getInstance(Application application){
        if(instance == null){
            mApplication = application;
            instance = new YSConfiguration();
        }
        
        return instance;
    }
    
    /**
     * get the feature code.
     * 
     * @return "YueShi" for YueShi version or "common" for common version
     */
    public String getFeatureCode(){
        if(mFeatureCode == null){
            mFeatureCode = (String)getProperties(key_feature);
        }
        
        return mFeatureCode;
    }

    /**
     * get the defualt server URL.
     * 
     * @return server URL
     */
    public String getDefualtServerUrl(){
        if(mServerUrl == null){
        	mServerUrl = (String)getProperties(key_defualt_server_url);
        }
        
        return mServerUrl;
    }
    
    /**
     * get boot apk package name when move to extend screen
     * 
     * @return apk package name, "None" means no apk to boot.
     */
    public String getBootPackageName(){
        if(mBootApkPackageName == null){
        	mBootApkPackageName = (String)getProperties(key_boot_apk_package_name);
        }
        
        return mBootApkPackageName;
    }
    
    /**
     * Whether need install install YSSysCtroller.apk.
     * 
     * @return
     */
    public Boolean isInstallYsctrl(){
        if(mIsNeedInstallYsctrl == null){
            String temp = getProperties(key_install_ysctrl);
            if(temp != null){
            	mIsNeedInstallYsctrl = Boolean.valueOf(temp);
            }
            else{
            	mIsNeedInstallYsctrl = false;
            }
        }
        
        return mIsNeedInstallYsctrl;
    }
    // get the property by key.
    private String getProperties(String key)
    {
        Context c = mApplication.getApplicationContext();
        String value = null;
        
        try{
            InputStream is = c.getAssets().open(configFileName);
            Properties properties = (new Properties());
            properties.load(is);
            value = properties.getProperty(key);
            
            is.close();
        }
        catch(IOException e){
            e.printStackTrace();
        }
        
        return value;
    }
}
