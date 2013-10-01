require(expm)

M <- matrix(data=c(0,0,0.05,0.05,
                0.05,0,0.05,0,
                0.05,0.05,0,0,
                0,0,0,0), nrow=4, byrow=T)

Q <- M - diag(rowSums(M))
rho <- max(-diag(Q))
R <- Q/rho + diag(4)

a <- 2
b <- 1
L <- 0.04879582996085663

muL <- rho*L
Pba <- expm(Q*L)[a+1,b+1]

n <- 0:200

getLogP <- function(x) {
    logP <- (R %^% x)[a+1,b+1] + x*log(muL) - lgamma(x+1) - muL - Pba
    return(exp(logP))
}
probs <- lapply(n, getLogP)
