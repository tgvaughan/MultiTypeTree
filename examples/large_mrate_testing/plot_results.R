dfsims <- list()
dfsamps <- list()

mvals <- c(0.125, 0.25, 0.5, 1, 2, 4, 8, 16, 32, 64)

simHeightMeans <- rep(0,length(mvals))
simHeightVars <- rep(0,length(mvals))
simCountMeans <- rep(0,length(mvals))
simCountVars <- rep(0,length(mvals))

sampHeightMeans <- rep(0,length(mvals))
sampHeightVars <- rep(0,length(mvals))
sampCountMeans <- rep(0,length(mvals))
sampCountVars <- rep(0,length(mvals))


for (i in seq(1,length(mvals))) {
    m <- mvals[i]
    simfname <- paste('heights',m,'.txt', sep='')
    sampfname <- paste('large_mrate_test_',m,'.log', sep='')

    dfsim <- read.table(simfname, header=T)
    simHeightMeans[i] <- mean(dfsim$h)
    simHeightVars[i] <- var(dfsim$h)
    simCountMeans[i] <- mean(dfsim$c)
    simCountVars[i] <- var(dfsim$c)
    
    dfsamp <- read.table(sampfname, header=T)
    sampHeightMeans[i] <- mean(dfsamp$tree.height)
    sampHeightVars[i] <- var(dfsamp$tree.height)
    sampCountMeans[i] <- mean(dfsamp$tree.count)
    sampCountVars[i] <- var(dfsamp$tree.count)
}


# Plot figure

pdf('largem_results.pdf', onefile=F, width=7, height=5)
par(mfrow=c(2,2))
par(mgp=c(1.5,0.5,0))
par(mar=c(3,3,1.5,0.5))

plot(mvals, simHeightMeans, 'o', col='red', xlab='m', ylab='tree height',
     main='tree height means', log='x')
lines(mvals, sampHeightMeans, 'o', col='blue')

plot(mvals, simCountMeans, 'o', col='red', xlab='m', ylab='migration count',
     main='migration count means', log='xy')
lines(mvals, sampCountMeans, 'o', col='blue')

plot(mvals, sqrt(simHeightVars), 'o', col='red', xlab='m', ylab='tree height',
     main='tree height std. dev.', log='x')
lines(mvals, sqrt(sampHeightVars), 'o', col='blue')

plot(mvals, sqrt(simCountVars), 'o', col='red', xlab='m', ylab='migration count',
     main='migration count std. dev.', log='xy')
lines(mvals, sqrt(sampCountVars), 'o', col='blue')

dev.off()
