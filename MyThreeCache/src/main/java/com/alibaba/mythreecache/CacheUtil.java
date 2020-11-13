package com.alibaba.mythreecache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.FileUtils;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

public class CacheUtil {
    private static CacheUtil instance;
    private Context context;
    private ImageCache imageCache;

    public CacheUtil(Context context) {
        this.context = context;
         Map<String, SoftReference<Bitmap>> cacheMap=new HashMap<>();
        this.imageCache = new ImageCache(cacheMap);

    }

    public  static synchronized CacheUtil getInstance(Context context){
        if(instance==null){
            instance=new CacheUtil(context);
        }
        return instance;
    }

    private void putBitmapIntoCache(String fileName,byte[] data){
        //将文件储存到内存中
        FileOutputStream fos=null;
        File file=new File(context.getFilesDir(),fileName);
        try {
            fos=new FileOutputStream(file);
            fos.write(data,0,data.length);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(fos!=null){
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        //将图片存入强引用
        imageCache.put(fileName, BitmapFactory.decodeByteArray(data,0,data.length));
    }

    public Bitmap getBitmapFromCache(String fileName){
        // 从强引用（LruCache）中取出图片
        Bitmap bm=null;
        bm=imageCache.get(fileName);
        if(bm==null){
            // 如果图片不存在强引用中，则去软引用（SoftReference）中查找
            Map<String,SoftReference<Bitmap>> cacheMap=imageCache.getCacheMap();
            SoftReference<Bitmap> softReference=cacheMap.get(fileName);
            if(softReference!=null){
                bm=softReference.get();
                imageCache.put(fileName,bm);
            }else {
                // 如果图片不存在软引用中，则去内存中找
                byte[] b=null;
                FileInputStream fis=null;
                ByteArrayOutputStream baos=null;
                try {
                    fis=context.openFileInput(fileName);
                    baos=new ByteArrayOutputStream();
                    byte[] tmp=new byte[1024];
                    int len=0;
                    while ((len=fis.read(tmp))!=-1){
                        baos.write(tmp,0,len);
                    }
                    b=baos.toByteArray();
                    bm=BitmapFactory.decodeByteArray(b,0,b.length);
                    imageCache.put(fileName,bm);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return bm;
    }

    public void setImageToView(final String path,final ImageView view){
        final String fileName=path.substring(path.lastIndexOf(File.separator)+1);
        Bitmap bitmap=getBitmapFromCache(fileName);
        if(bitmap!=null){
            view.setImageBitmap(bitmap);
        }else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] b = HttpUtil.getInstance().getByteArrayFromWeb(path);
                    if (b != null && b.length > 0) {
                        // 将图片字节数组写入到缓存中
                        putBitmapIntoCache(fileName, b);
                        final Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length);
                        // 将从网络获取到的图片设置给ImageView
                        view.post(new Runnable() {
                            @Override
                            public void run() {
                                view.setImageBitmap(bm);
                            }
                        });
                    }
                }
            }).start();
        }
    }
}
