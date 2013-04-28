package org.qii.weiciyuan.ui.maintimeline;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;
import org.qii.weiciyuan.R;
import org.qii.weiciyuan.bean.AccountBean;
import org.qii.weiciyuan.bean.MessageListBean;
import org.qii.weiciyuan.bean.UnreadBean;
import org.qii.weiciyuan.bean.UserBean;
import org.qii.weiciyuan.bean.android.AsyncTaskLoaderResult;
import org.qii.weiciyuan.bean.android.MentionTimeLineData;
import org.qii.weiciyuan.bean.android.TimeLinePosition;
import org.qii.weiciyuan.dao.maintimeline.MainMentionsTimeLineDao;
import org.qii.weiciyuan.support.database.MentionsTimeLineDBTask;
import org.qii.weiciyuan.support.error.WeiboException;
import org.qii.weiciyuan.support.lib.TopTipBar;
import org.qii.weiciyuan.support.utils.AppEventAction;
import org.qii.weiciyuan.support.utils.BundleArgsConstants;
import org.qii.weiciyuan.support.utils.GlobalContext;
import org.qii.weiciyuan.support.utils.Utility;
import org.qii.weiciyuan.ui.adapter.StatusListAdapter;
import org.qii.weiciyuan.ui.basefragment.AbstractMessageTimeLineFragment;
import org.qii.weiciyuan.ui.browser.BrowserWeiboMsgActivity;
import org.qii.weiciyuan.ui.interfaces.ICommander;
import org.qii.weiciyuan.ui.loader.MentionsWeiboMsgLoader;
import org.qii.weiciyuan.ui.loader.MentionsWeiboTimeDBLoader;
import org.qii.weiciyuan.ui.main.MainTimeLineActivity;

/**
 * User: qii
 * Date: 12-7-29
 */
public class MentionsWeiboTimeLineFragment extends AbstractMessageTimeLineFragment<MessageListBean> {

    private AccountBean accountBean;
    private UserBean userBean;
    private String token;
    private UnreadBean unreadBean;
    private TimeLinePosition timeLinePosition;
    private MessageListBean bean = new MessageListBean();


    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    public MessageListBean getList() {
        return bean;
    }

    public MentionsWeiboTimeLineFragment() {

    }

    public MentionsWeiboTimeLineFragment(AccountBean accountBean, UserBean userBean, String token) {
        this.accountBean = accountBean;
        this.userBean = userBean;
        this.token = token;
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(newBroadcastReceiver, new IntentFilter(AppEventAction.NEW_MSG_BROADCAST));
    }

    @Override
    public void onPause() {
        super.onPause();
        saveTimeLinePositionToDB();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(newBroadcastReceiver);
    }

    private void saveTimeLinePositionToDB() {
        timeLinePosition = Utility.getCurrentPositionFromListView(getListView());
        timeLinePosition.newMsgIds = newMsgTipBar.getValues();
        MentionsTimeLineDBTask.asyncUpdatePosition(timeLinePosition, accountBean.getUid());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setRetainInstance(false);

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("account", accountBean);
        outState.putSerializable("bean", bean);
        outState.putSerializable("userBean", userBean);
        outState.putString("token", token);
        outState.putSerializable("unreadBean", unreadBean);
        outState.putSerializable("timeLinePosition", timeLinePosition);
    }


    public void refreshUnread(TopTipBar topTipBar) {
//        LeftMenuFragment fragment = (LeftMenuFragment) getParentFragment().getFragmentManager().findFragmentByTag(LeftMenuFragment.class.getName());
//            fragment.setMentionWeiboUnreadCount(topTipBar.getValues().size());
    }

    @Override
    protected void onListViewScrollStop() {
        super.onListViewScrollStop();
        timeLinePosition = Utility.getCurrentPositionFromListView(getListView());
    }


    @Override
    protected void buildListAdapter() {
        StatusListAdapter adapter = new StatusListAdapter(this, ((ICommander) getActivity()).getBitmapDownloader(), getList().getItemList(), getListView(), true, true);
        adapter.setTopTipBar(newMsgTipBar);
        timeLineAdapter = adapter;
        getListView().setAdapter(timeLineAdapter);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisible() && isVisibleToUser) {
            ((MainTimeLineActivity) getActivity()).setCurrentFragment(this);
            if (getActivity().getActionBar().getTabAt(0).getText().toString().contains(")")) {
                getPullToRefreshListView().startRefreshNow();
            }
        }
    }


    @Override
    protected void newMsgOnPostExecute(MessageListBean newValue) {
        if (getActivity() != null && newValue.getSize() > 0) {
            addNewDataAndRememberPosition(newValue);
        }
        unreadBean = null;
        refreshUnread(newMsgTipBar);
        NotificationManager notificationManager = (NotificationManager) getActivity()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(Long.valueOf(GlobalContext.getInstance().getCurrentAccountId()).intValue());


    }

    private void addNewDataAndRememberPosition(MessageListBean newValue) {
        newMsgTipBar.setValue(newValue, false);
        int size = newValue.getSize();
        if (getActivity() != null && newValue.getSize() > 0) {
            getList().addNewData(newValue);
            int index = getListView().getFirstVisiblePosition();
            View v = getListView().getChildAt(1);
            int top = (v == null) ? 0 : v.getTop();
            getAdapter().notifyDataSetChanged();
            int ss = index + size;
            getListView().setSelectionFromTop(ss + 1, top);
            MentionsTimeLineDBTask.asyncReplace(getList(), accountBean.getUid());
            saveTimeLinePositionToDB();
        }
    }

    @Override
    protected void oldMsgOnPostExecute(MessageListBean newValue) {
        if (newValue != null && newValue.getSize() > 1) {
            getList().addOldData(newValue);
        } else {
            Toast.makeText(getActivity(), getString(R.string.older_message_empty), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        switch (getCurrentState(savedInstanceState)) {
            case FIRST_TIME_START:
                getLoaderManager().initLoader(DB_CACHE_LOADER_ID, null, dbCallback);
                break;
            case ACTIVITY_DESTROY_AND_CREATE:
                userBean = (UserBean) savedInstanceState.getSerializable("userBean");
                accountBean = (AccountBean) savedInstanceState.getSerializable("account");
                token = savedInstanceState.getString("token");
                unreadBean = (UnreadBean) savedInstanceState.getSerializable("unreadBean");
                timeLinePosition = (TimeLinePosition) savedInstanceState.getSerializable("timeLinePosition");

                Loader<MentionTimeLineData> loader = getLoaderManager().getLoader(DB_CACHE_LOADER_ID);
                if (loader != null) {
                    getLoaderManager().initLoader(DB_CACHE_LOADER_ID, null, dbCallback);
                }

                MessageListBean savedBean = (MessageListBean) savedInstanceState.getSerializable("bean");
                if (savedBean != null && savedBean.getSize() > 0) {
                    getList().replaceData(savedBean);
                    timeLineAdapter.notifyDataSetChanged();
                    refreshLayout(getList());
                }

                break;
        }
        refreshUnread(this.newMsgTipBar);
    }


    @Override
    protected void listViewItemClick(AdapterView parent, View view, int position, long id) {
        Intent intent = new Intent(getActivity(), BrowserWeiboMsgActivity.class);
        intent.putExtra("msg", bean.getItemList().get(position));
        intent.putExtra("token", token);
        startActivity(intent);
    }

    private void setListViewPositionFromPositionsCache() {
        if (timeLinePosition != null)
            getListView().setSelectionFromTop(timeLinePosition.position + 1, timeLinePosition.top);
        else
            getListView().setSelectionFromTop(0, 0);

        setListViewUnreadTipBar(timeLinePosition);

    }

    private void setListViewUnreadTipBar(TimeLinePosition p) {
        if (p != null && p.newMsgIds != null)
            newMsgTipBar.setValue(p.newMsgIds);
    }

    @Override
    protected MessageListBean getDoInBackgroundNewData() throws WeiboException {
        return null;
    }


    @Override
    protected MessageListBean getDoInBackgroundOldData() throws WeiboException {
        return null;
    }

    @Override
    protected MessageListBean getDoInBackgroundMiddleData(String beginId, String endId) throws WeiboException {
        MainMentionsTimeLineDao dao = new MainMentionsTimeLineDao(token);
        dao.setMax_id(beginId);
        dao.setSince_id(endId);
        MessageListBean result = dao.getGSONMsgList();
        return result;
    }

    @Override
    public void loadMiddleMsg(String beginId, String endId, String endTag, int position) {
        getLoaderManager().destroyLoader(NEW_MSG_LOADER_ID);
        getLoaderManager().destroyLoader(OLD_MSG_LOADER_ID);
        getPullToRefreshListView().onRefreshComplete();
        dismissFooterView();

        Bundle bundle = new Bundle();
        bundle.putString("beginId", beginId);
        bundle.putString("endId", endId);
        bundle.putString("endTag", endTag);
        bundle.putInt("position", position);
        getLoaderManager().restartLoader(MIDDLE_MSG_LOADER_ID, bundle, msgCallback);

    }

    @Override
    public void loadNewMsg() {
        getLoaderManager().destroyLoader(MIDDLE_MSG_LOADER_ID);
        getLoaderManager().destroyLoader(OLD_MSG_LOADER_ID);
        dismissFooterView();
        getLoaderManager().restartLoader(NEW_MSG_LOADER_ID, null, msgCallback);
    }

    @Override
    protected void loadOldMsg(View view) {
        getLoaderManager().destroyLoader(NEW_MSG_LOADER_ID);
        getPullToRefreshListView().onRefreshComplete();
        getLoaderManager().destroyLoader(MIDDLE_MSG_LOADER_ID);
        getLoaderManager().restartLoader(OLD_MSG_LOADER_ID, null, msgCallback);
    }

    private LoaderManager.LoaderCallbacks<MentionTimeLineData> dbCallback = new LoaderManager.LoaderCallbacks<MentionTimeLineData>() {
        @Override
        public Loader<MentionTimeLineData> onCreateLoader(int id, Bundle args) {
            getPullToRefreshListView().setVisibility(View.INVISIBLE);
            return new MentionsWeiboTimeDBLoader(getActivity(), GlobalContext.getInstance().getCurrentAccountId());
        }

        @Override
        public void onLoadFinished(Loader<MentionTimeLineData> loader, MentionTimeLineData result) {
            getPullToRefreshListView().setVisibility(View.VISIBLE);

            if (result != null) {
                getList().replaceData(result.msgList);
                timeLinePosition = result.position;
            }

            getAdapter().notifyDataSetChanged();
            setListViewPositionFromPositionsCache();
            refreshLayout(bean);

            /**
             * when this account first open app,if he don't have any data in database,fetch data from server automally
             */

            if (bean.getSize() == 0) {
                pullToRefreshListView.startRefreshNow();
            }

            getLoaderManager().destroyLoader(loader.getId());

            /**when one user open app from android notification center while this app is using another account,
             * activity will restart, and then mentions and comment fragment
             * will fetch new message from server
             **/
            //            if (getActivity() != null && getActivity().getActionBar().getTabAt(1).getText().toString().contains(")")) {
            //                pullToRefreshListView.startRefreshNow();
            //            }
        }

        @Override
        public void onLoaderReset(Loader<MentionTimeLineData> loader) {

        }
    };

    protected Loader<AsyncTaskLoaderResult<MessageListBean>> onCreateNewMsgLoader(int id, Bundle args) {
        String accountId = accountBean.getUid();
        String token = accountBean.getAccess_token();
        String sinceId = null;
        if (getList().getItemList().size() > 0) {
            sinceId = getList().getItemList().get(0).getId();
        }
        return new MentionsWeiboMsgLoader(getActivity(), accountId, token, sinceId, null);
    }

    protected Loader<AsyncTaskLoaderResult<MessageListBean>> onCreateMiddleMsgLoader(int id, Bundle args, String middleBeginId, String middleEndId, String middleEndTag, int middlePosition) {
        String accountId = accountBean.getUid();
        String token = accountBean.getAccess_token();
        return new MentionsWeiboMsgLoader(getActivity(), accountId, token, middleBeginId, middleEndId);
    }

    protected Loader<AsyncTaskLoaderResult<MessageListBean>> onCreateOldMsgLoader(int id, Bundle args) {
        String accountId = accountBean.getUid();
        String token = accountBean.getAccess_token();
        String maxId = null;
        if (getList().getItemList().size() > 0) {
            maxId = getList().getItemList().get(getList().getItemList().size() - 1).getId();
        }
        return new MentionsWeiboMsgLoader(getActivity(), accountId, token, null, maxId);
    }

    private BroadcastReceiver newBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            AccountBean account = (AccountBean) intent.getSerializableExtra(BundleArgsConstants.ACCOUNT_EXTRA);
            if (account == null || !account.getUid().equals(account.getUid())) {
                return;
            }
            MessageListBean data = (MessageListBean) intent.getSerializableExtra(BundleArgsConstants.MENTIONS_WEIBO_EXTRA);
            if (data != null) {
                addNewDataAndRememberPosition(data);
            }
        }
    };
}

