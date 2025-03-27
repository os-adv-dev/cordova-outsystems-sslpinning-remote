const fs = require('fs');
const path = require('path');

module.exports = function(context) {
    const webClientPath = path.join(context.opts.projectRoot, 'platforms/android/app/src/main/java/com/outsystems/plugins/loader/clients/WebClient.java');

    if (fs.existsSync(webClientPath)) {
        let content = fs.readFileSync(webClientPath, 'utf-8');

        const insertAfter = /Uri uri = Uri\.parse\(url\);/;

        if (content.match(insertAfter)) {
        
            const codeToAdd = `
        boolean isFirebaseRemoteAlreadyFetch = preferences.getBoolean("isSSLFirebaseRemoteFetch", false);
        if (isFirebaseRemoteAlreadyFetch) {
            String path = uri.getPath();
            Pattern staticExtensions = Pattern.compile("(?i).*\\.(json|map|woff|woff2|ttf|otf|svg|png|jpe?g)$");
            if (path != null && staticExtensions.matcher(path).matches()) {
                return null;
            }
            
            WebResourceResponse sslValidation = this.addPinningWebClient.getSSLUrlValidation(url);
            if (sslValidation != null) {
                return sslValidation;
            }
        }`;

        
            content = content.replace(insertAfter, (match) => match + codeToAdd);

            content = content.replace(
                /(this\.logger = logger;)/,
                `$1\n        this.addPinningWebClient = new AddPinningWebClient();`
            );
    
            content = content.replace(
                /(private CordovaPreferences preferences;)/,
                `$1\n    private AddPinningWebClient addPinningWebClient;`
            );
    
           
            if (!content.includes('import com.outsystems.plugins.sslpinning.AddPinningWebClient;')) {
                content = content.replace(
                    /(import [a-zA-Z.]*;\n)+/,
                    `$&import com.outsystems.plugins.sslpinning.AddPinningWebClient;\n`
                );
            }

           
            fs.writeFileSync(webClientPath, content, 'utf-8');

            console.error(' -- ✅ -- Code updated to WebClient Java SSL Pinning Remote ');
        } else {
            console.error(' -- ❌ -- File WebClient.java not found in the project');
        }
    } else {
        console.error(' -- ❌ -- The file  WebClient.java is not exist in this project!');
    }
};
