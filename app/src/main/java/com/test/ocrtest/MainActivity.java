package com.test.ocrtest;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.flyco.dialog.listener.OnOperItemClickL;
import com.flyco.dialog.widget.ActionSheetDialog;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.jph.takephoto.app.TakePhoto;
import com.jph.takephoto.app.TakePhotoImpl;
import com.jph.takephoto.model.InvokeParam;
import com.jph.takephoto.model.TContextWrap;
import com.jph.takephoto.model.TResult;
import com.jph.takephoto.model.TakePhotoOptions;
import com.jph.takephoto.permission.InvokeListener;
import com.jph.takephoto.permission.PermissionManager;
import com.jph.takephoto.permission.TakePhotoInvocationHandler;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Wolf on 2017/11/6
 */


public class MainActivity extends AppCompatActivity implements TakePhoto.TakeResultListener, InvokeListener {
    @BindView(R.id.get_photo)
    Button getPhoto;
    @BindView(R.id.ocr)
    Button ocr;
    @BindView(R.id.photo_image)
    ImageView photoImage;
    @BindView(R.id.ocr_text)
    TextView ocrText;
    private TakePhoto takePhoto;
    private InvokeParam invokeParam;
    final String[] stringItems = {"拍照", "选择照片"};
    private Context mContext;
    /**
     * TessBaseAPI初始化用到的第一个参数，是个目录。
     */
    private static final String DATAPATH = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;

    /**
     * 在DATAPATH中新建这个目录，TessBaseAPI初始化要求必须有这个目录。
     */
    private static final String tessdata = DATAPATH + File.separator + "tessdata";
    /**
     * TessBaseAPI初始化测第二个参数，就是识别库的名字不要后缀名。
     */
    private static final String DEFAULT_LANGUAGE = "chi_sim";
    /**
     * assets中的文件名
     */
    private static final String DEFAULT_LANGUAGE_NAME = DEFAULT_LANGUAGE + ".traineddata";
    /**
     * 保存到SD卡中的完整文件名
     */
    private static final String LANGUAGE_PATH = tessdata + File.separator + DEFAULT_LANGUAGE_NAME;
    private String path;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getTakePhoto().onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mContext = this;
        RxPermissions rxPermissions = new RxPermissions(this);
        rxPermissions.request(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE).subscribe(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean aBoolean) throws Exception {
                if (aBoolean) {
                    copyToSD(LANGUAGE_PATH, DEFAULT_LANGUAGE_NAME);
                }
            }
        });


    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        getTakePhoto().onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    /**
     * 获取TakePhoto实例
     *
     * @return
     */
    public TakePhoto getTakePhoto() {
        if (takePhoto == null) {
            takePhoto = (TakePhoto) TakePhotoInvocationHandler.of(this).bind(new TakePhotoImpl(this, this));
        }
        return takePhoto;
    }

    @OnClick({R.id.get_photo, R.id.ocr})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.get_photo:
                getPhoto();
                break;
            case R.id.ocr:
                startOcr();
                break;
        }
    }

    private void photo(int type) {
        File file = new File(Environment.getExternalStorageDirectory(), "/ocrtest/" + System.currentTimeMillis() + ".jpg");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        Uri imageUri = Uri.fromFile(file);
        takePhoto.onEnableCompress(null, false);
        TakePhotoOptions.Builder builder = new TakePhotoOptions.Builder();
        takePhoto.setTakePhotoOptions(builder.create());
        if (type == 1) {
            takePhoto.onPickFromCapture(imageUri);
        } else {
            takePhoto.onPickFromGallery();
        }
    }

    private void getPhoto() {
        final ActionSheetDialog dialog = new ActionSheetDialog(mContext, stringItems, null);
        dialog.setOnOperItemClickL(new OnOperItemClickL() {
            @Override
            public void onOperItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        photo(1);
                        dialog.dismiss();
                        break;
                    case 1:
                        photo(2);
                        dialog.dismiss();
                        break;
                    default:
                        break;
                }
            }
        });
        dialog.isTitleShow(false).show();
    }

    private void startOcr() {
        if (path.isEmpty()) {
            return;
        }
        final ProgressDialog progress = new ProgressDialog(mContext);
        progress.setMessage("正在解析");
        progress.show();
        final long startTime = System.currentTimeMillis();
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(@NonNull ObservableEmitter<String> e) throws Exception {
                Bitmap bitmap = BitmapFactory.decodeFile(path);

                TessBaseAPI tessBaseAPI = new TessBaseAPI();

                tessBaseAPI.init(DATAPATH, DEFAULT_LANGUAGE);

                tessBaseAPI.setImage(bitmap);
                String resultString = tessBaseAPI.getUTF8Text();
                Log.i("识别结果", resultString);
                e.onNext(resultString);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {

            }

            @Override
            public void onNext(@NonNull String s) {
                ocrText.setText((System.currentTimeMillis() - startTime) / 1000 + "秒" + "\n" + s);
                progress.dismiss();
            }

            @Override
            public void onError(@NonNull Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionManager.TPermissionType type = PermissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionManager.handlePermissionsResult(this, type, invokeParam, this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        getTakePhoto().onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void takeSuccess(TResult result) {
        Log.i("takeSuccess", "takeSuccess：" + result.getImage().getOriginalPath());
        Glide.with(mContext).load("file://" + result.getImage().getOriginalPath()).into(photoImage);
        path = result.getImage().getOriginalPath();
    }

    @Override
    public void takeFail(TResult result, String msg) {
        Log.i("takeFail", "takeFail:" + msg);
    }

    @Override
    public void takeCancel() {
        Log.i("takeCancel", getResources().getString(R.string.msg_operation_canceled));
    }

    @Override
    public PermissionManager.TPermissionType invoke(InvokeParam invokeParam) {
        PermissionManager.TPermissionType type = PermissionManager.checkPermission(TContextWrap.of(this), invokeParam.getMethod());
        if (PermissionManager.TPermissionType.WAIT.equals(type)) {
            this.invokeParam = invokeParam;
        }
        return type;
    }

    /**
     * 将assets中的识别库复制到SD卡中
     *
     * @param path 要存放在SD卡中的 完整的文件名。这里是"/storage/emulated/0//tessdata/chi_sim.traineddata"
     * @param name assets中的文件名 这里是 "chi_sim.traineddata"
     */
    public void copyToSD(String path, String name) {

        //如果存在就删掉
        File f = new File(path);
        if (f.exists()) {
            f.delete();
        }
        if (!f.exists()) {
            File p = new File(f.getParent());
            if (!p.exists()) {
                p.mkdirs();
            }
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        InputStream is = null;
        OutputStream os = null;
        try {
            is = this.getAssets().open(name);
            File file = new File(path);
            os = new FileOutputStream(file);
            byte[] bytes = new byte[2048];
            int len = 0;
            while ((len = is.read(bytes)) != -1) {
                os.write(bytes, 0, len);
            }
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null)
                    is.close();
                if (os != null)
                    os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
