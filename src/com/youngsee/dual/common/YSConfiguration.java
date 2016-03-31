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
    private final static String    key_need_bootanimation  = "need_boot_animation";
    
    public final static String     FEATURE_CODE_YUESHI     = "YueShi";
    public final static String     FEATURE_CODE_COMMON     = "common";
    
    private static YSConfiguration instance                = null;
    
    // For get the application context. It should has same life circle with
    // application.
    // Because this configuration is global in application.
    private static Application     mApplication            = null;
    
    private String                 mFeatureCode            = null;
    private Boolean                mHasBootAnimation       = null;
    
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
     * Check that whether need boot animation
     * 
     * @return
     */
    public Boolean hasBootAnimation(){
        if(mHasBootAnimation == null){
            String temp = getProperties(key_need_bootanimation);
            if(temp != null){
                mHasBootAnimation = Boolean.valueOf(temp);
            }
            else{
                mHasBootAnimation = false;
            }
        }
        
        return mHasBootAnimation;
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
