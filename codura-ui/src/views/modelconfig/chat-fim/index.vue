<template>
  <div class="app-container">
    <!-- 页面标题 -->
    <el-divider content-position="left"><span style="font-size: 18px;">模型参数配置</span></el-divider>

    <el-tabs v-model="activeTab" type="card">
      <!-- 聊天模型配置 -->
      <el-tab-pane label="聊天模型配置" name="chat">
        <el-form :model="chatConfig" label-width="180px">
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="最大生成 Token 数">
                <el-input v-model="chatConfig.maxTokens" placeholder="请输入最大生成 Token 数" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="温度参数">
                <el-input v-model="chatConfig.temperature" placeholder="请输入温度参数 (0~1)" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="历史对话轮数">
                <el-input v-model="chatConfig.historyTurns" placeholder="请输入历史对话轮数" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-tab-pane>

      <!-- 补全模型配置 -->
      <el-tab-pane label="补全模型配置" name="fim">
        <el-form :model="fimConfig" label-width="180px">
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="最大生成文本行数">
                <el-input v-model="fimConfig.maxLines" placeholder="请输入最大生成行数" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="最大生成 Token 数">
                <el-input v-model="fimConfig.maxTokens" placeholder="请输入最大生成 Token 数" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="温度参数">
                <el-input v-model="fimConfig.temperature" placeholder="请输入温度参数 (0~1)" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="开启文件上下文">
                <el-switch v-model="fimConfig.enableFileContext" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="开启连续补全">
                <el-switch v-model="fimConfig.enableContinuousCompletion" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="开启补全缓存">
                <el-switch v-model="fimConfig.enableCompletionCache" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="开启多行补全">
                <el-switch v-model="fimConfig.enableMultilineCompletion" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="是否开启补全">
                <el-switch v-model="fimConfig.enableCompletion" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <!-- 底部操作按钮 -->
    <el-divider />
    <div class="text-center">
<!--      <el-button @click="reset" icon="el-icon-refresh">重置</el-button>-->
      <el-button type="warning" @click="loadDefault" icon="el-icon-refresh-left">默认配置</el-button>
      <el-button type="primary" @click="save" icon="el-icon-check">保存</el-button>
    </div>
  </div>
</template>

<script>
import {getFimConfigByUserName,getChatConfigByUserName,addChatConfig,addFimConfig,updateChatConfig,updateFimConfig} from "@/api/config/chat-fim";
import {parseTime} from "@/utils/xunmeng";
export default {
  name: 'ModelConfig',
  data() {
    return {

      activeTab: 'chat',
      chatConfig: {
        maxTokens: undefined,
        temperature: 0,
        historyTurns: 0
      },
      fimConfig: {
        maxLines: undefined,
        maxTokens: undefined,
        temperature: 0,
        enableFileContext: false,
        enableContinuousCompletion: false,
        enableCompletionCache: false,
        enableMultilineCompletion: false,
        enableCompletion: false
      },

    }
  },
  created() {
    this.init();
  },
  methods: {
    init() {
      const userName = this.$store.state.user.name;
      Promise.all([
        getChatConfigByUserName(userName),
        getFimConfigByUserName(userName)
      ])
          .then(([chatRes, fimRes]) => {
            const chatData = chatRes.data;
            const fimData = fimRes.data;

            // 判断当前用户是否有配置
            const hasChat = !!chatData;
            const hasFim = !!fimData;

            const adminPromises = [];

            if (!hasChat) {
              adminPromises.push(
                  getChatConfigByUserName('admin').then(res => {
                    this.chatConfig = { ...this.chatConfig, ...res.data };
                  })
              );
            } else {
              this.chatConfig = { ...this.chatConfig, ...chatData };
            }

            if (!hasFim) {
              adminPromises.push(
                  getFimConfigByUserName('admin').then(res => {
                    this.fimConfig = { ...this.fimConfig, ...res.data };
                  })
              );
            } else {
              this.fimConfig = { ...this.fimConfig, ...fimData };
            }

            // 如果需要从 admin 加载，就等这些 promise 执行完
            return Promise.all(adminPromises);
          })
          .catch(() => {
            // 用户请求出错，全部退回 admin
            return this.getUserConfig('admin');
          })
    },

    getUserConfig(userName) {
      return Promise.all([
        getChatConfigByUserName(userName),
        getFimConfigByUserName(userName)
      ]).then(([chatRes, fimRes]) => {
        const chatData = chatRes.data;
        const fimData = fimRes.data;

        if (chatData) {
          this.chatConfig = { ...this.chatConfig, ...chatData };
        }

        if (fimData) {
          this.fimConfig = { ...this.fimConfig, ...fimData };
        }
      });
    },

    save(){
      const now = parseTime(new Date());
      const chatConfig = { ...this.chatConfig, updateTime: now };
      const fimConfig = { ...this.fimConfig, updateTime: now };
      console.log(chatConfig);
      const saveFim = this.fimConfig.id == null
          ? addFimConfig(fimConfig)
          : updateFimConfig(fimConfig);

      const saveChat= this.chatConfig.id == null
          ? addChatConfig(chatConfig)
          : updateChatConfig(chatConfig);

      Promise.all([
        saveFim,
        saveChat
      ]).then(() => {
        this.$message.success('保存成功');
      }).catch(() => {
        this.$message.error('保存失败');
      });
    },

    loadDefault() {
      this.getUserConfig('admin');
      this.$message.success('恢复系统默认');
    }
  }
}
</script>

<style scoped>
.footer {
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px dashed #dcdfe6;
  text-align: center;
}
</style>
