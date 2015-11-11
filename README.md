# cordova-plugin-passive-geolocation-service
Background passive geolocation service for Android

## 功能特点
* 使用 Android 的 Passive 位置源获被动地获取信息，可在后台以极低的电量记录用户到过的地方
* 自动将位置信息上传到服务器（需要自行编写服务器端程序）
* 可设定上传位置的时间间隔
* 上传位置失败（或未达到预设的上传时间间隔）时，会将位置信息保存到本地数据库中，等待下次上传
* 在连接到 WiFi 网络时可自动上传尚未上传的位置信息


## 用法
```javascript
/// 设置并启动服务
window.pgs.configure({
startOnBoot: true,                      /// 是否开机自动启动服务
    minDistance: 10,                    /// 仅当位置变动大于此值时才进行位置更新，单位：米（详见 Android 文档中的 LocationManager 章节）
    minTime: 1 * 1000,                  /// 最小定位时间间隔，单位：毫秒（详见 Android 文档中的 LocationManager 章节）
    desiredAccuracy: 1000,              /// 仅接收定位精度小于此值的位置更新，单位：米
    distanceFilter: 10,                 /// 当位置变动大于此值时才记录位置，单位：米
    debug: false,                       /// 调试开关，调试模式下定位成功时会发出声音和提示
    minUploadInterval: 5 * 60 * 1000,   /// 最小上传间隔    
    appLocalUID: getUUID(),              /// 本地保存的 uuid（唯一用户身份编号或 token，用于校验用户身份）
    uploadOldByCell: false,             /// 是否通过数据连接上传旧位置信息
    maxIdleTime: 5 * 60 * 1000,         /// 获得位置信息后，如果距离上一次定位时间超过此值，则无论如何都会上传最新获得的位置信息
});
window.pgs.start()


/// 修改配置
window.pgs.stop();
window.pgs.configure(newSettings);
window.pgs.start();
```

## 修改服务器保存位置的 API 地址

在源码中搜索 latitude.greensea.org，将其替换成你自己的地址即可。用户的位置信息通过 POST 方法以 JSON 格式上传，JSON 格式如下：

```json
[
  {"latitude": "24.118293", "longitude": "106.579118", "altitude": "618", "accuracy": "24", "src": "gps", "time": 1447143895}
]
```

以 JSON 表示的位置信息是一个数组，数组中的元素是位置信息，位置信息中部分字段的意义是：
* src: 位置来源，可以是 gps 或 network
* time: 定位时间，获得此位置信息的时间

如果只上传一个位置信息，那么数组中就只有一个元素；如果上传多个位置信息，那么数组中就有多个位置元素。

当操作成功时，API 应该输出 ok 字样（即 printf("ok")），如果操作失败，直接输出错误信息即可。当操作失败时，位置信息会保存到本地的 SQLite 数据库中，并在一段时间过后重试上传。
