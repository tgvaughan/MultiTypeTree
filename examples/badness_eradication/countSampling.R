require(expm)

# Set up matrices
Q <- matrix(data=c(-208.516676, 208.516676, 0.002952, -0.002952),
            nrow=2, ncol=2, byrow=T)
rho <- max(-diag(Q))
R <- Q/rho + diag(2)

lambda <- 1.008736926575113
mu <- 208.5166757820231
a <- 0
b <- 0

N <- 2

# Slow but correct count sampling function:
sampleCount <- function(sampleSize) {

    res <- rep(0,sampleSize)
    for (i in 1:sampleSize) {
        cat(paste("Sample",i,"\n"))
        repeat {
            n <- rpois(1, mu*lambda)
            P_b_given_na <- (R %^% n)[a+1, b+1]
            if (runif(1)<P_b_given_na)
                break
        }
        res[i] <- n
    }

    return (res)
}

# Fast sampling function of questionable correctness:
sampleCountFast <- function(sampleSize) {

    Pba <- expm(Q*lambda)[a+1,b+1]
    PLab <- 0
    for (n in seq(0,N)) {
        PLab <- PLab + (R %^% n)[a+1,b+1]*(mu*lambda)^n/factorial(n)
    }
    PLab <- PLab * exp(-mu*lambda) / Pba


    res <- rep(0, sampleSize)
    for (i in 1:sampleSize) {

        if (runif(1)<PLab) {
            repeat {
                n <- rpois(1, mu*lambda)
                if ((n<=N) && (runif(1) < (R %^% n)[a+1,b+1]))
                    break;
            }
        } else {
            repeat {
                n <- rpois(1, mu*lambda)
                if (n>N)
                    break;
            }
        }
        
        res[i] <- n
    }

    return (res)
}
