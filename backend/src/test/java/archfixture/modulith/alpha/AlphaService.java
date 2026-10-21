package archfixture.modulith.alpha;

import archfixture.modulith.beta.BetaApi;

public class AlphaService {

    public String call(BetaApi beta) {
        return beta.name();
    }
}
