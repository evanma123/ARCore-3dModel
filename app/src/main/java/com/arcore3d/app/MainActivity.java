package com.arcore3d.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.Config;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.Light;
import com.google.ar.sceneform.rendering.Color;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_FILE_REQUEST_CODE = 1001;
    private static final int PERMISSION_REQUEST_CODE = 1002;
    
    private ArFragment arFragment;
    private ModelRenderable modelRenderable;
    private Button btnSelectFile;
    private Button btnClearModel;
    private Node currentModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 减少ARCore的日志输出
        System.setProperty("arcore_logging_level", "ERROR");
        
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);
        btnSelectFile = findViewById(R.id.btn_select_file);
        btnClearModel = findViewById(R.id.btn_clear_model);
        
        // 检查权限
        checkAndRequestPermissions();
        
        // 设置按钮控制
        setupButtonControls();
        
        
        setUpPlane();
        
        // 更新按钮状态
        updateButtonStates();
    }

    /**
     * 更新按钮状态
     */
    private void updateButtonStates() {
        boolean hasModel = currentModel != null;
        btnClearModel.setEnabled(hasModel);
    }

    /**
     * 设置按钮控制
     */
    private void setupButtonControls() {
        // 选择本地模型按钮
        btnSelectFile.setOnClickListener(v -> {
            openFilePicker();
        });

        // 清除模型按钮
        btnClearModel.setOnClickListener(v -> {
            clearAllModels();
        });
    }

    /**
     * 清除场景中所有模型
     */
    private void clearAllModels() {
        if (arFragment != null && arFragment.getArSceneView() != null) {
            Scene scene = arFragment.getArSceneView().getScene();
            
            // 清除所有锚点节点（包含模型）
            List<Node> nodesToRemove = new ArrayList<>();
            for (Node node : scene.getChildren()) {
                if (node instanceof AnchorNode) {
                    nodesToRemove.add(node);
                }
            }
            
            for (Node node : nodesToRemove) {
                scene.removeChild(node);
            }
            
            // 重置状态
            currentModel = null;
            
            // 更新按钮状态
            updateButtonStates();
            
            Log.d("ARApp", "已清除所有模型");
        }
    }

    /**
     * 设置AR平面点击事件
     */
    private void setUpPlane() {
        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (modelRenderable == null) {
                        Log.d("ARApp", "模型未加载，无法放置");
                        return;
                    }

                    try {
                        // 创建锚点
                        Anchor anchor = hitResult.createAnchor();
                        AnchorNode anchorNode = new AnchorNode(anchor);
                        anchorNode.setParent(arFragment.getArSceneView().getScene());

                        // 创建TransformableNode启用内置手势操作
                        TransformableNode transformableNode = new TransformableNode(arFragment.getTransformationSystem());
                        transformableNode.setParent(anchorNode);
                        transformableNode.setRenderable(modelRenderable);
                        
                        // 设置缩放范围（默认是0.75-1.75，我们调整为0.1-5.0）
                        transformableNode.getScaleController().setMinScale(0.1f);
                        transformableNode.getScaleController().setMaxScale(5.0f);
                        
                        transformableNode.select();
                        
                        // 保存当前模型引用
                        currentModel = transformableNode;
                        
                        // 更新按钮状态
                        updateButtonStates();

                        Log.d("ARApp", "模型已放置，启用内置手势操作");
                    } catch (Exception e) {
                        Log.e("ARApp", "创建模型时出错: " + e.getMessage(), e);
                        Toast.makeText(this, "创建模型时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * 检查并请求必要的文件读取权限
     */
    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的权限模型
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 
                        PERMISSION_REQUEST_CODE);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-12 使用传统权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 
                        PERMISSION_REQUEST_CODE);
            }
        }
    }
    
    /**
     * 打开文件选择器
     */
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {
            "model/gltf-binary", 
            "model/gltf+json", 
            "application/octet-stream",
            "application/gltf-buffer"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.putExtra(Intent.EXTRA_TITLE, "选择3D模型文件");
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE);
    }
    

    
    /**
     * 从本地文件加载模型
     */
    private void loadModelFromFile(Uri fileUri) {
        try {
            Log.d("ARApp", "开始加载模型文件: " + fileUri.toString());
            
            // 将content:// URI转换为临时文件
            File tempFile = createTempFileFromUri(fileUri);
            if (tempFile == null) {
                Log.e("ARApp", "无法创建临时文件");
                Toast.makeText(this, "无法处理选中的文件", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String fileName = tempFile.getName().toLowerCase();
            RenderableSource.SourceType sourceType = fileName.endsWith(".gltf") ? 
                RenderableSource.SourceType.GLTF2 : RenderableSource.SourceType.GLB;
            
            Uri fileUriForLoading = Uri.fromFile(tempFile);
            Log.d("ARApp", "使用转换后的文件路径: " + fileUriForLoading.toString());
            
            ModelRenderable.builder()
                    .setSource(this, RenderableSource.builder().setSource(
                            this,
                            fileUriForLoading,
                            sourceType)
                            .setScale(0.75f)
                            .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                            .build())
                    .setRegistryId(fileUriForLoading.toString())
                    .build()
                    .thenAccept(renderable -> {
                        modelRenderable = renderable;
                        Log.d("ARApp", "模型加载成功，可以点击屏幕放置模型了");
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "本地模型加载成功，请点击屏幕放置模型", Toast.LENGTH_LONG).show();
                        });
                    })
                    .exceptionally(throwable -> {
                        Log.e("ARApp", "模型加载失败: " + throwable.getMessage(), throwable);
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "模型加载失败: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                        });
                        return null;
                    });
        } catch (Exception e) {
            Log.e("ARApp", "文件处理错误: " + e.getMessage(), e);
            Toast.makeText(this, "文件处理错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 将content:// URI转换为临时文件
     */
    private File createTempFileFromUri(Uri uri) {
        try {
            ContentResolver contentResolver = getContentResolver();
            String mimeType = contentResolver.getType(uri);
            
            // 获取文件扩展名
            String extension = ".glb"; // 默认扩展名
            if (mimeType != null) {
                if (mimeType.contains("gltf")) extension = ".gltf";
                else if (mimeType.contains("glb")) extension = ".glb";
            }
            
            // 从文件名获取扩展名
            String fileName = getFileName(uri);
            if (fileName != null && fileName.contains(".")) {
                extension = fileName.substring(fileName.lastIndexOf("."));
            }
            
            // 创建临时文件
            File tempFile = File.createTempFile("ar_model_", extension, getCacheDir());
            tempFile.deleteOnExit(); // 应用退出时删除
            
            // 复制内容到临时文件
            try (InputStream inputStream = contentResolver.openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                
                if (inputStream == null) {
                    Log.e("ARApp", "无法打开输入流");
                    return null;
                }
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                Log.d("ARApp", "临时文件创建成功: " + tempFile.getAbsolutePath() + ", 大小: " + tempFile.length() + " bytes");
                return tempFile;
            }
        } catch (Exception e) {
            Log.e("ARApp", "创建临时文件失败: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 从URI获取文件名
     */
    private String getFileName(Uri uri) {
        try {
            ContentResolver contentResolver = getContentResolver();
            android.database.Cursor cursor = contentResolver.query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    return cursor.getString(nameIndex);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("ARApp", "获取文件名失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 分析GLB文件结构
     */
    private void analyzeGLBFile(File file) {
        try {
            byte[] header = new byte[12];
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.read(header);
                
                // 检查GLB文件头
                if (header[0] == 'g' && header[1] == 'l' && header[2] == 'T' && header[3] == 'F') {
                    Log.d("ARApp", "GLB格式验证通过");
                } else {
                    Log.w("ARApp", "警告：可能不是有效的GLB文件");
                }
                
                // 读取文件大小
                int fileLength = (header[8] & 0xFF) | ((header[9] & 0xFF) << 8) | 
                                ((header[10] & 0xFF) << 16) | ((header[11] & 0xFF) << 24);
                Log.d("ARApp", "GLB文件长度: " + fileLength);
            }
        } catch (Exception e) {
            Log.e("ARApp", "分析GLB文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return "";
        }
        return name.substring(lastIndexOf + 1).toLowerCase();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri fileUri = data.getData();
                loadModelFromFile(fileUri);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要文件读取权限才能选择本地模型", Toast.LENGTH_LONG).show();
            }
        }
    }
}
