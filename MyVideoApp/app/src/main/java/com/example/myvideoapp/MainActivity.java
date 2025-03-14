package com.example.myvideoapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private Fragment currentFragment = null;
    private boolean isConnected = false;
    private BottomNavigationView bottomNavigationView;
    private MainFragment mainFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottomNavigationView);

        if (!isConnected) {
            // 显示连接界面
            ConnectFragment connectFragment = new ConnectFragment();
            loadFragment(connectFragment, connectFragment.getClass().getName());
            currentFragment = connectFragment;
            bottomNavigationView.setVisibility(View.GONE); // 隐藏底部导航栏
        } else {
            // 初始化各 Fragment
            mainFragment = new MainFragment();
            final SettingsFragment settingsFragment = new SettingsFragment();
            final LocalFilesFragment localFilesFragment = new LocalFilesFragment();

            if (savedInstanceState == null) {
                loadFragment(mainFragment, mainFragment.getClass().getName());
                currentFragment = mainFragment;
            }
            // 底部导航栏切换
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                Fragment targetFragment = null;

                if (id == R.id.nav_main) {
                    targetFragment = mainFragment;
                } else if (id == R.id.nav_local_files) {
                    targetFragment = localFilesFragment;
                } else if (id == R.id.nav_settings) {
                    targetFragment = settingsFragment;
                }

                if (targetFragment != null) {
                    loadFragment(targetFragment);
                    return true;
                }
                return false;
            });
            bottomNavigationView.setVisibility(View.VISIBLE); // 显示底部导航栏
        }
    }

    /**
     * 加载 Fragment
     */
    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (currentFragment != null) {
            transaction.hide(currentFragment);
        }
        if (fragment.isAdded()) {
            transaction.show(fragment);
        } else {
            transaction.add(R.id.mainFragment, fragment, fragment.getClass().getName());
        }
        transaction.commit();
        currentFragment = fragment;
    }

    /**
     * 预加载 Fragment
     */
    private void loadFragment(Fragment fragment, String tag) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        // 如果 Fragment 已经存在，直接返回
        if (fragmentManager.findFragmentByTag(tag) != null) {
            return;
        }

        fragmentTransaction.add(R.id.mainFragment, fragment, tag);
        fragmentTransaction.commit();
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
        if (connected) {
            // 连接成功，切换到主界面
            mainFragment = new MainFragment();
            mainFragment.setConnected(true);
            final SettingsFragment settingsFragment = new SettingsFragment();
            final LocalFilesFragment localFilesFragment = new LocalFilesFragment();

            // 移除 ConnectFragment
            FragmentManager fragmentManager = getSupportFragmentManager();
            Fragment connectFragment = fragmentManager.findFragmentByTag(ConnectFragment.class.getName());
            if (connectFragment != null) {
                FragmentTransaction transaction = fragmentManager.beginTransaction();
                transaction.remove(connectFragment);
                transaction.commit();
            }

            // 底部导航栏切换
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                Fragment targetFragment = null;

                if (id == R.id.nav_main) {
                    targetFragment = mainFragment;
                } else if (id == R.id.nav_local_files) {
                    targetFragment = localFilesFragment;
                } else if (id == R.id.nav_settings) {
                    targetFragment = settingsFragment;
                }

                if (targetFragment != null) {
                    loadFragment(targetFragment);
                    return true;
                }
                return false;
            });
            bottomNavigationView.setVisibility(View.VISIBLE); // 显示底部导航栏
            bottomNavigationView.setSelectedItemId(R.id.nav_main);//选中主界面菜单项
        }
    }
}