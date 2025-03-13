package com.example.myvideoapp;
import static androidx.core.content.ContentProviderCompat.requireContext;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private Fragment currentFragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化各 Fragment
        final SettingsFragment settingsFragment = new SettingsFragment();
        final MainFragment mainFragment = new MainFragment();

        // 默认加载第一个 Fragment
        if (savedInstanceState == null) {
            loadFragment(mainFragment, mainFragment.getClass().getName());
            // 设置当前 Fragment
            currentFragment = mainFragment;
        }

        // 底部导航栏切换
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment targetFragment = null;

            if (id == R.id.nav_main) {
                targetFragment = mainFragment;
            } else if (id == R.id.nav_settings) {
                targetFragment = settingsFragment;
            }

            if (targetFragment != null) {
                loadFragment(targetFragment);
                return true;
            }
            return false;
        });
    }

    /**
     * 加载 Fragment
     */
    // 修改 MainActivity 的 loadFragment 方法
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
}