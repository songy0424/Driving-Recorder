package com.example.myvideoapp;
import android.os.Bundle;
import android.util.Log;
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
        // 可以根据需要添加其他 Fragment

        // 默认加载第一个 Fragment
        if (savedInstanceState == null) {
            loadFragment(mainFragment, mainFragment.getClass().getName());
            // 设置当前 Fragment
            currentFragment = mainFragment;
        }

        // 底部导航栏切换
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment targetFragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_main) {
                loadFragment(mainFragment);
            } else if (id == R.id.nav_settings) {
                loadFragment(settingsFragment);
            } else {
                // Handle other cases
            }
            if (targetFragment != null) {
                loadFragment(targetFragment);
//                currentFragment = targetFragment;
            }
            return true;
        });
    }

    /**
     * 加载 Fragment
     */
    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction();
        if (currentFragment != null) {
            transaction.hide(currentFragment);
        }
        if (!fragment.isAdded()) {
            transaction.add(R.id.nav_host_fragment, fragment, fragment.getClass().getName());
        } else {
            transaction.show(fragment);
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

        fragmentTransaction.add(R.id.nav_host_fragment, fragment, tag);
        fragmentTransaction.commit();
    }
}