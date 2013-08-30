#!/usr/bin/env python

from scipy import *
import scipy.stats as stats
from matplotlib import pylab

seterr(divide='raise')

class State:
    x = None
    xStored = None
    
    def __init__(self, x0):
        self.x = x0

    def store(self):
        self.xStored = self.x

    def restore(self):
        self.x = self.xStored

def proposeJump (state):
    logHR = 0
    
    if state.x>0:
        logHR = log(stats.beta.pdf(state.x,1,5))
        state.x = 0
    else:
        state.x = stats.beta.rvs(1,5)
        logHR = -log(stats.beta.pdf(state.x,1,5))

    return logHR

def proposeScale (state):
#    if state.x>0:
    fmin = 0.9
    fmax = 1/0.9
    f = stats.uniform.rvs(fmin,(fmax-fmin))
    
    state.x *= f

    return -log(f)
    
#    else:
#        return -float('inf')
        

def targetDensity (state):

    p0 = 0.05
    
    if state.x>0:
        if state.x<1:
            return log((1-p0)*stats.beta.pdf(state.x,2,2))
        else:
            return -float('inf')
    else:
        return log(p0)


def MCMC ():
    
    burninFrac = 0.2
    maxiter = 100000
    sampPeriod = 10
        
    chain = []
    
    state = State(0.5)

    for i in range(maxiter):

        state.store()

        logAlpha = -targetDensity(state)
        
        logHR = 0.0
        if stats.uniform.rvs(0,1)<0.5:
            logHR = proposeJump(state)
        else:
            logHR = proposeScale(state)

        logAlpha += targetDensity(state) + logHR
            
        if stats.uniform.rvs(0,1)>exp(logAlpha):
            state.restore()

        if i>maxiter*burninFrac and i%sampPeriod==0:
            chain.append(state.x)

    return chain


if __name__ == '__main__':

    chain = MCMC()

    zeroFrac = sum([float(x==0) for x in chain])/len(chain)
    print "Zero fraction = {}".format(zeroFrac)
    
    pylab.hist(chain, bins=50)
    pylab.show()
