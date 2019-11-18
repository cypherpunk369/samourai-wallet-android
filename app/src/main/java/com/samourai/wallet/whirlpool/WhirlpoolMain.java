package com.samourai.wallet.whirlpool;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.samourai.wallet.R;
import com.samourai.wallet.api.APIFactory;
import com.samourai.wallet.util.AppUtil;
import com.samourai.wallet.whirlpool.fragments.WhirlpoolCyclesFragment;
import com.samourai.wallet.whirlpool.models.Cycle;
import com.samourai.wallet.whirlpool.newPool.NewPoolActivity;
import com.samourai.wallet.widgets.ViewPager;

import org.bitcoinj.core.Coin;

import java.util.ArrayList;

public class WhirlpoolMain extends AppCompatActivity {

    private RecyclerView CycleRecyclerView;
    private WhirlpoolCyclesFragment dashboard, inProgressCycles, completedCycles;
    private ArrayList<Cycle> cycles = new ArrayList();
    private String tabTitle[] = {"Dashboard", "In Progress", "Completed"};
    private ViewPager cyclesViewPager;
    private TabLayout cyclesTabLayout;
    private TextView totalAmountToDisplay;
    private TextView amountSubText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whirlpool_main);
        Toolbar toolbar = findViewById(R.id.toolbar_whirlpool);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        totalAmountToDisplay = findViewById(R.id.whirlpool_total_amount_to_display);
        amountSubText = findViewById(R.id.toolbar_subtext);
        cyclesViewPager = findViewById(R.id.whirlpool_viewpager);
        cyclesTabLayout = findViewById(R.id.whirlpool_home_tabs);
        cyclesTabLayout.setupWithViewPager(cyclesViewPager);
        dashboard = new WhirlpoolCyclesFragment();
        inProgressCycles = new WhirlpoolCyclesFragment();
        completedCycles = new WhirlpoolCyclesFragment();
        cyclesViewPager.enableSwipe(true);

        CyclesViewPagerAdapter adapter = new CyclesViewPagerAdapter(getSupportFragmentManager());
        cyclesViewPager.setAdapter(adapter);
        cyclesViewPager.setCurrentItem(1);
        findViewById(R.id.whirlpool_fab).setOnClickListener(view -> startActivity(new Intent(this, NewPoolActivity.class)));

        cyclesViewPager.addOnPageChangeListener(new android.support.v4.view.ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                switch (position) {
                    //Dashboard
                    case 0: {
                        amountSubText.setText(R.string.total_being_cycled);
                        long value = APIFactory.getInstance(WhirlpoolMain.this).getXpubBalance();
                        totalAmountToDisplay.setText(Coin.valueOf(value).toPlainString() + " BTC");
                        break;
                    }

                    case 1: {
                        //In-Progress
                        amountSubText.setText(R.string.premix_balance);
                        long value = APIFactory.getInstance(WhirlpoolMain.this).getXpubPreMixBalance();
                        totalAmountToDisplay.setText(Coin.valueOf(value).toPlainString() + " BTC");
                        break;
                    }

                    case 2: {
                        //Completed
                        amountSubText.setText(R.string.post_mix_balance);
                        long value = APIFactory.getInstance(WhirlpoolMain.this).getXpubPostMixBalance();
                        totalAmountToDisplay.setText(Coin.valueOf(value).toPlainString() + " BTC");
                        break;
                    }

                }

            }

            @Override
            public void onPageSelected(int position) { }

            @Override
            public void onPageScrollStateChanged(int state) { }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_whirl_pool_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onResume() {
        super.onResume();

        AppUtil.getInstance(WhirlpoolMain.this).checkTimeOut();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    class CyclesViewPagerAdapter extends FragmentPagerAdapter {


        CyclesViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0: {
                    return dashboard;
                }
                case 1: {
                    return inProgressCycles;

                }
                case 2: {
                    return completedCycles;
                }
            }
            return dashboard;
        }

        @Override
        public int getCount() {
            return tabTitle.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return tabTitle[position];
        }
    }


}
